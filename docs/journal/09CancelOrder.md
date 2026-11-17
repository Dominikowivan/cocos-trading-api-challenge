# CANCEL ORDER

Finalmente, buscar cancelar ordenes.

El endpoint recibe solo (ademas del header de idempotencia): userId, orderId.

userId se podria sacar el orderId, pero es consistente con todos los otros endpoints y nos ahorra un viaje a la base para buscar el id en base al orderId.

mismas validacion de siempre de si el userId existe o no.
validacion de si el orderId existe o no. Validacion de si es un estado valido para cancelar (solo new por ahora).
Hay que validar pertenencia tambien, que al userId le pertenezca la orden que quiere cancelar.
Y reasonOF en la tabla historial deberia ser "USER_REQUESTED"

Si se quiere cancelar una orden ya cancelada, estado no cancelable.

Para ser cohesivos con el endpoint de placement de orden, el endpoint va a ser un POST a /api/v1/users/{userId}/orders/{orderId}/cancel/

Tambien es idempotente. Tambien se lockea por usuario.
Y obviamente, se registra en la tabla de cambio de estado.

