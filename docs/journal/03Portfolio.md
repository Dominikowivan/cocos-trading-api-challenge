# Portfolio

Bien, vamos a arrancar con el primer endpoint del proyecto.

> "Portfolio: La respuesta deberá devolver el valor total de la cuenta de un usuario, sus pesos disponibles para operar y el listado de activos que posee (incluyendo cantidad de acciones, el valor total monetario de la posición ($) y el rendimiento total (%))."

Repaso las cosas que ya anoté:

Según los requerimientos funcionales, siempre se usan las órdenes en estado "FILLED" para calcular los atributos de los activos.

- Valor total de la cuenta del usuario.
- Pesos disponibles para operar.
- Listado de activos, donde un activo tiene:
  - cantidad de acciones: suma de todas las órdenes FILLED de ese usuario para un instrumento dado.
  - valor total monetario de la posición: cantidad de acciones x último close del instrumento dado.
  - rendimiento total: si el usuario está en rendimiento positivo o negativo frente a sus inversiones. Vamos a usar la estrategia de weighted average.

Agrego el challenge.md a documentos porque es la fuente de verdad de los requerimientos del proyecto y va a servir a la hora de trabajar con SDD.


Para conocer cuál es el valor de una cuenta se necesita saber:

# Pesos que tenés

- Settled cash (plata que tiene): todas las FILLED. Size x precio, con el signo del lado (SELL y CASH_IN suman) (BUY y CASH_OUT restan). Es la plata que ya se movió.
- Reserved cash (plata que tiene reservada en órdenes BUY LIMIT en NEW): lo mismo, size x precio.

# Acciones que tenés

- Holdings: ACCIONES FILLED. Se agrupan por instrumento, así cada uno tiene su data:
  - cantidad
  - promedio
  - filtra posiciones con cantidad 0. Es importante recorrer la historia porque si compraste y vendiste la misma cantidad, el promedio cambia: arrancás de 0.
    el promedio se reinicia al precio de la orden que cruzó y arranca del otro lado. De todas formas al calcular ganancia, no estamos calculando posiciones en short porque ahora mismo las tomamos como una anomalía.
Pero si necesitáramos soportarlas, ya está listo el promedio del holding.

- Reserved shares: las órdenes que se prometieron vender. SELL LIMIT en NEW.

De acá podemos derivar entonces:
- Dinero disponible = settledCash − reservedCash
- Cuántas acciones disponibles tenemos = holdings − reservedShares.


Finalmente: valor total de la cuenta del usuario. Se cuenta con el settledCash y el valor de las acciones que tiene en el mercado en ese momento (mirando el close).

Para repasar:
Holdings es lo que tenés y lo que te costó. De acá vamos a calcular el promedio de cuánto te costó, que se usa después para armar el rendimiento total.

Lo importante acá es que el promedio solo cambia cuando aumentás la exposición. Vender algo que ya tenías no te cambia el promedio, porque eso ya es ganancia realizada. El promedio se saca sobre cuánto promedia todo lo que pagaste.
Si cerraste una posición, se resetea el promedio.

Position es lo que representa hoy en el mercado el instrumento (mirando el close de marketdata)

Arranco con el specify.

## Clarificaciones

### Primera clarificación

> **Question 1: Negative position**
>
> Context: Edge Cases — "the dataset leaves user 1 holding BMA -10 (more sold than bought)."
>
> What we need to know: Is a negative position reported as-is, filtered out, or flagged — and what is its total return?

Es el caso de anomalía de BMA que termina dando -10 de posición total para el usuario.

Vamos a reportar "as-is" para no romper la app, porque es un error ya concretado, y pienso que es mejor ser transparente con el usuario.

En la API, ahora mismo, por cómo funciona, uno de los requerimientos funcionales es que un usuario no pueda estar en short.
Estamos decidiendo qué hacer si se da el caso de que la base reporta que un usuario tiene una posición en short de todas formas.

Si es una regla de negocio rota, lo mejor es hacer ruido, que el equipo técnico sepa que algo salió mal.
Vamos a fallar con un log a nivel de error con el id de usuario, instrumento y cantidad.

### Segunda clarificación

> **Question 2: Held instrument with no market data**
>
> Context: User Story 2, scenario 3 — PGR and IRCP are held but have no marketdata row.
>
> What we need to know: What market value and total return does such a position report?

Acá la diferencia con el caso anterior es que no es una regla rota para la API.
Puede pasar que es un instrumento que el usuario tiene, pero que aún no cotizó.
Logueamos warning para no continuar silenciosamente. Al usuario le mostramos lo que sabemos: cuál es su posición, pero marketData: null.
Null siempre en vez de valores "0", porque 0 indica una cantidad válida que no es real.
El valor total de la cuenta también quedaría en null, porque si decimos cualquier otro número mentimos.

La única excepción serían los instrumentos de tipo "Moneda", porque cada unidad tiene valor por sí misma.

Lo que sí cabe aclarar: en los seeds no veo que este caso suceda, es un caso defensivo.

### Tercera clarificación

> **Question 3: Unknown user id**
>
> Context: Edge Cases — the id is untrusted input (ADR-0007).
>
> What we need to know: Is a request for a non-existent user id rejected as not found, or answered with an empty portfolio?

404 Not Found. Por defecto para cualquier búsqueda por id no encontrada.

## Plan

Listo, vamos al plan y revisarlo.

Me propuso hacer 3 queries:

1. Existencia del usuario: OK. Es una validación clásica.
2. Traerse todas las órdenes, en cualquier estado, como entidades JPA completas (eeeeh, ¿es necesario?).
   Por cada orden, en base a su estado y lado, decide qué hacer. (Me parece OK que esto resida en la app: lógica de negocio.)
3. Trae el último precio de las acciones para armar la info de los activos.

Lo que me parece bien:

- Reglas de negocio en Java, la existencia de usuario para decidir si cortar ahí o no.
- REPEATABLE_READ en la transacción. Como hacemos múltiples reads, es mejor hacerlos sobre un snapshot. De otra forma puede quedar inconsistente.

Lo que no me parece bien:

- Traer órdenes que no usa: las CANCELLED y REJECTED no aportan nada.

Lo que queda a revisar una vez que genere el código:
Las órdenes que trae, las pasa a Java. ¿Van a encapsular lógica de negocio o van a ser solo datos de lectura? Porque armar objetos Java solo para leerlos, y que no tengan lógica, es estado manejado sin sentido. Con records bastaría si es ese caso.

Corregí el plan con la observación de las órdenes que no tiene sentido traerse, y lo acepto.

El resto en el plan queda muy en abstracto; prefiero revisar el código y hacer correcciones una vez generado (siempre antes de commitear).

Corro tasks, implement, y vamos a ver.

## Revisión del código

Ya estuve revisando el código. Primeras impresiones: bien, pero hay algunas cosas a refactorizar con las que estoy en desacuerdo.

1. La orden se desglosa en el modelo a "Ledger". O sea, ordenRepository devuelve LedgerEntry para cada orden.
   En todos lados del challenge se le dice orden, en la base de datos se le dice orden, y no pensamos en algún momento cercano refactorizar esa tabla, así que, para facilidad de comprensión, es mejor renombrarlo a Orden y operar con eso.

2. Después, veo mucho código que podría dividirse un poco en subtareas para mejor comprensión y lectura.
   Acá falta alguna regla en la constitución que lo haga respetar.

   En la clase "AccountLedger", para calcular el settledCash y el reservedCash, inicia un contador y va recorriendo las órdenes para ir sumando a ese contador. Se podría solucionar con un fold, o un map + sum.

   Agregué un task template para que en cada task se ejecute un paso de refactor aplicando los principios de la constitución al finalizarlos.

3. Después, organización de código. Dividió en dos carpetas: ledger y portfolio.

   La carpeta de modelo también quedó muy bolsa de gatos, todo dentro de dos carpetas distintas. La reorganicé para juntar dentro de una misma carpeta lo que es cohesivo entre sí.

Finalmente, como retrospectiva, quedó grande el feature con SDD. Creo que el endpoint de búsqueda hubiera sido más chico y mejor para empezar, así se encaraba este de portfolio con una estructura ya armada sobre la cual basarse.