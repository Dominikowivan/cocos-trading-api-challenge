# Order Placement

Bueno llegamos al fin.

Arrancamos como siempre, repasando lo ya analizado y construyendo sobre eso:

### 3. Enviar una orden al mercado

- **instrumentId**: para qué instrumento es la orden.
- **userId**: quién compra.
- **type**: se soporta la orden de tipo MARKET o LIMIT. 
- **side**: implemento BUY primero y SELL después. El endpoint solo acepta BUY y SELL: un depósito o una extracción no es una orden al mercado, ya que no tiene instrumento operable, ni precio de ejecución, ni validación de fondos contra el mercado, así que CASH_IN/CASH_OUT existen en el modelo y entran en el cálculo del efectivo, pero no se crean por acá.
- **price**: para LIMIT. MARKET no necesita.
- **size**: cuántas acciones.
- **monto total de inversión**: los requerimientos funcionales piden que se pueda enviar una cantidad total a invertir.

Size y monto total de inversión son mutuamente excluyentes. O indicás el total de acciones que querés, o hasta cuánto querés pagar y comprás esa cantidad.
Tambien, vamos a poner otro endpoint para transferencias de MONEDAS. La logica de moneda no tiene sentido con MARKET. decir "size" y "hasta este tope" es lo mismo.
Va a ser poco cohesivo para una orden tipo MONEDA usar este endpoint, y pensandolo como un tipo de instrumento distinto, prefiero que tenga su propia logica encapsulada en su propio metodo
declarando sus propios atributos que son cohesivos a su tipo de instrumento propio.

Tambien se mantiene la decision que habia hecho sobre cancelar una orden:
Cada orden que ponemos con el POST de order debería ser NEW/FILLED (dependiendo del tipo de orden). Si queremos cancelar a través de ese endpoint, deberíamos poner un flag al estilo "cancelOrder" en vez de "status" y que reciba "NEW" o "CANCEL". El problema que tiene este approach es que en status CANCEL, casi todo el resto de los parámetros pasan a no tener sentido.
Dado esto, pienso que es mejor tener un endpoint aparte para cancelar una orden.

Con los limites puestos en que hace este endpoint, vamos a hacer todo para MARKET primero, después todo para LIMIT. Uno a la vez.

## MARKET

Buy/Sell en side.
Price todavia no existe, market no lo necesita.

Size: Cuantas acciones se compran.
Monto total a invertir en pesos.

Size y monto son mutualmente excluyentes.

- Para size buy:
Se tiene que validar que el usuario tenga el montoDisponible en caso de BUY de lastclose * size.
Si los tiene se tiene que poner la orden de MARKET BUY ACCION con el size pedido, en FILLED.
si no, la orden queda rejected

- para size sell:
Se tiene que validar que el usuario tenga la cantidad disponible de acciones pedidas a vender.
Si no, la orden queda rejected.


 "Si la orden enviada es por un monto mayor al disponible, la orden tiene que ser rechazada y guardarse en estado REJECTED".
- Para monto total a invertir BUY:


preciomanzana= las manzanas salen 10.
BUY, montoTotal $100, tenés dineroDisponibles $110.
dineroDisponibles < precioManzana = false
sizeMaximoAComprar= floor(montoTotal/preciomanzana) = 10 manzanas.
sizeMaximoQuePodesPagar= floor(dineroDisponibles/preciomanzana) = 11 manzanas.

size: sizeMaximoAComprar > sizeMaximoQuePodesPagar ?: sizeMaximoAComprar
compras 10 manzanas.


preciomanzana= las manzanas salen 10.
BUY, montoTotal $100, tenés disponibles $5.
dineroDisponibles < precioManzana = REJECTED


preciomanzana= las manzanas salen 10.
BUY, montoTotal $100, tenés dineroDisponibles $10.
dineroDisponibles < montoTotal = Rejected.


"Si la orden enviada es por un monto mayor al disponible, la orden tiene que ser rechazada y guardarse en estado REJECTED".


- Para monto total a invertir SELL:

dineroDisponibles = accionesDisponibles x close
dineroDisponibles < montoTotal = Rejected.


preciomanzana= las manzanas salen 10.
SELL, montoTotal $100, tenés disponibles 11 manzanas ($110). 

floor(montoTotal/preciomanzana) = 10 manzanas.
Tenés 11 ≥ 10 → vendés 10 y te queda 1.



preciomanzana= las manzanas salen 200.
SELL, montoTotal $100.
precioManzana > montoTotal. Rejected.


preciomanzana= las manzanas salen 10.
SELL, montoTotal $100, tenés disponibles 1 manzanas.

dineroDisponibles = $10
dineroDisponibles < montoTotal = Rejected.


preciomanzana= las manzanas salen 10.
SELL, montoTotal $100, tenés disponibles 10 manzanas.

dineroDisponibles = $100
dineroDisponibles < montoTotal = false.
floor(montoTotal/preciomanzana) = 10 manzanas.
Tenés 10 = 10 → vendés 10.


Lo que queda, idéntico para los dos lados:

disponible = BUY  -> efectivo disponible
             SELL -> posicionDisponible × close

si disponible < montoTotal   -> REJECTED
size = floor(montoTotal /close)
si size == 0                 -> REJECTED

y para limit la verdad que es la misma cuenta pero cambia que en vez de close (en las dos partes de la ecualcion) va el price enviado.
Ahi lo que te frena es el precio que pusiste, no el precio de mercado




Concurrencia y tolerancia a fallos:
Agregamos  idempotencia para evitar que el cliente mande dos veces una operacion y esta se repita. 

Hacemos una tabla nueva con userId, uuid, hash del request y order_id.
(userId, uuid) unique, y sobre esto tambien ponemos el "ON CONFLICT", asi si hay dos transacciones del mismo usuario, con la misma uuid, y el mismo request, la segunda o cualquiera que venga despues
se frena ahi.

Del on conflict> do nothing.
Si no devuelve nada la query, es por que cayo en el do nothing, ya se proceso ese request.  usamos el hash del request para comparar y ver que se guardo. Si el hash es el mismo, es que ese pedido ya se proceso, usamos el order_id en esa tabla para buscarlo y devoler la respuesta al usuario.
Si el hash es otro, es que ya se quemo ese uuid (es infima la chance, pero puede ser un bug del cliente).

Importante que la idempotencia sea read commited, sino la segunda transaccion no ve lo que la primera comiteo cuando sale del conflicto.

Bueno, esto va PREVIO y ORTOGONAL a lo que tiene que pasar en el resto de la transaccion, asi que en una primera iteracion de SDD no lo agregamos.
Que quede como task para el final, asi primero revisamos bien que el resto de la logica del endpoint que no es poca cosa ande bien. 

Lo mismo con lo proximo que es bueno ,tamos protegidos de dos requests iguales y repetidos... pero que pasa con dos requests distintos y simultaneos?
Ahi no podemos tener dos request leyendo el disponible, y commiteando dos ordenes asumiendo ese mismo disponible ya leido.

Necesitamos lockear la request a nivel usuario (no tiene sentido lockear otros usuarios).
El costo a pagar es que se secuencian las ordenes de los usuarios a nivel base de datos, lo cual me parece bien.

Para eso vamos a hacer un selectbyID del userid con pessimistic lock. Bloquear asumiendo que puede haber conflicto.
Despues de eso podemos hacer el resto de la transaccion, sin preocuparnos por repeatable read por que todo el resto
de la informacion que el usuario necesita para calcular la validez de su orden no se va a mover.

Aca lo mismo, el pessimistic lock puede ir al final despues de implementar toda la logica de negocio. No agreguemos complejidad a algo ya complejo de entrada.

Las iteraciones entonce serian:

- Market con size. Buy/sell. 
- Market con tope. Buy/sell.

- LIMIT con size. Buy/sell. 
- LIMIT con tope. Buy/sell. 

- Lock pesimista bajo userID
- idempotencia
- Alguna linea en el readme que indique que nuestros test funcionales son los e2e. El enunciado pedia uno explicitamente pero ya eran parte de nuestro ciclo de desarollo.
