# Portfolio

Bien vamos a arrancar con el primer endpoint del proyecto.

"Portfolio: La respuesta deberá devolver el valor total de la cuenta de un usuario, sus pesos disponibles para operar y el listado de activos que posee (incluyendo cantidad de acciones, el valor total monetario de la posición ($) y el rendimiento total (%))."

Repaso las cosas que ya anote:

Según los requerimientos funcionales, siempre se usan las órdenes en estado "FILLED" para calcular los atributos de los activos.

Valor total de la cuenta del usuario:
Pesos disponibles para operar:
Listados de activos, donde un activo tiene:
- cantidad de acciones: sum de todas las órdenes filled de ese usuario para un instrumento dado.
- valor total monetario de la posición: cantidad de acciones x último closed  del instrumento dado.
- rendimiento total: Si el usuario está en rendimiento positivo o negativo frente a sus inversiones. Vamos a usar la estrategia de weighted average.

Vamos a hacer el feature, pero hacer un atributo del endpoint por cada vuelta de SDD.
Primero cantidad de acciones.
Despues valor total monetario.
Despues rendimiento total.

Agrego el challenge.md a documentos por que es la fuente de verdad de los requerimientos del proyecto y va a servir a la hora de trabajar con SDD.

