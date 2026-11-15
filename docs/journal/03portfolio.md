# Portfolio

Bien vamos a arrancar con el primer endpoint del proyecto.

"Portfolio: La respuesta deberá devolver el valor total de la cuenta de un usuario, sus pesos disponibles para operar y el listado de activos que posee (incluyendo cantidad de acciones, el valor total monetario de la posición ($) y el rendimiento total (%))."

Repaso las cosas que ya anote:

Según los requerimientos funcionales, siempre se usan las órdenes en estado "FILLED" para calcular los atributos de los activos.

- Valor total de la cuenta del usuario:
- Pesos disponibles para operar:
  - Listados de activos, donde un activo tiene:
    - cantidad de acciones: sum de todas las órdenes filled de ese usuario para un instrumento dado.
    - valor total monetario de la posición: cantidad de acciones x último closed  del instrumento dado.
    - rendimiento total: Si el usuario está en rendimiento positivo o negativo frente a sus inversiones. Vamos a usar la estrategia de weighted average.


Agrego el challenge.md a documentos por que es la fuente de verdad de los requerimientos del proyecto y va a servir a la hora de trabajar con SDD.

Arranco con el specify.

Primera clarificacion:
"
Question 1: Negative position

Context: Edge Cases — "the dataset leaves user 1 holding BMA -10 (more sold than bought)."
What we need to know: Is a negative position reported as-is, filtered out, or flagged — and what is its total return?"
"

Es el caso de anomalia de BMA que termina dando -10 de posicion total para le usuario.

Vamos a reportar "as-is" para no romper la app, por que es un error ya concretado, y pienso que es mejor ser transparente con el usuario

En la api, ahora mismo por como funciona, uno de los requerimientos funcionales es que un usuario no pueda estar en short.
Estamos decidiendo que hacer si se da el caso que la base reporta que un usuario tiene una posicion en short de todas formas.

Si es una regla de negocio rota, lo mejor es hacer ruido, que el equipo tecnico sepa que algo salio mal.
Vamos a fallar con un log a nivel de error con el id de usuario, instrumento y cantidad. 

""
Question 2: Held instrument with no market data

Context: User Story 2, scenario 3 — PGR and IRCP are held but have no marketdata row.

What we need to know: What market value and total return does such a position report?
"

Aca la diferencia con el caso anterior es que no es una regla rota para la api.
Puede pasar que es un instrumento que el usuario tiene, pero que aun no cotizo.
Logueamos warning para no continuar silenciosamente. Al usuario Le mostramos lo que sabemos: Cual es su posicion, pero marketData: null.
Null siempre en vez de valores "0", por que 0 indica una cantidad valida que no es real.
El valor total de la cuenta tambien quedaria en null, por que si decimos cualquier otro numero mentimos.

La unica excepcion serian los instrumentos de tipo "Moneda" por que cada unidad tiene valor por si misma.

Lo que si cabe aclarar: en los seeds no veo que este caso suceda, es un caso defensivo.

"Question 3: Unknown user id

Context: Edge Cases — the id is untrusted input (ADR-0007).

What we need to know: Is a request for a non-existent user id rejected as not found, or answered with an empty portfolio?

*""

404 not found. Por defecto para cualquier busqueda por id no encontrada.

Listo, vamos al plan y revisarlo.

# Summary: 

Me propuso hacer 3 queries.
1- Existencia del usuario: OK. Es una validacion clasica.
2- Traerse todas las ordenes, en cualquier estado, como entidades JPA completas (eeeeh, es necesario?)
Por cada orden, en base a su estado y lado decide que hacer. (Me parece OK que esto recida en la app, logica de negocio)
3- Trae el ultimo precio de las acciones para armar la info de los activos.

Lo que me parece bien: 
Reglas de negocio en java, la existencia de usuario para decidir si cortar ahi o no.
Repeatable_read en el transaction. Como hacemos multiples reads, es mejor hacerlos sobre un snapshot. De otra forma puede quedar inconsistente.

Lo que no me parece bien:
-Traer ordenes que no usa, las canceled y rejected no aportan nada. 

Lo que queda a revisar una vez genere el codigo:
Las ordenes que trae, las pasa a java. Van a encapsular logica de negocio o van a ser solo datos de lectura? por que armar objetos java, solo para leerlos y que no tengan logica
es estado manejado sin sentido. Con records bastaria si es ese caso. 

Corregi el plan con la observacion de las ordenes que no tiene sentido traerse y lo acepto.

El resto en el plan queda muy en abstracto, prefiero revisar el codigo y hacer correcciones una vez generado (siempre antes de commitear).

Corro tasks, implement, y vamos a ver.
