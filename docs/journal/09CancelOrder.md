# CANCEL ORDER

Finalmente, buscar cancelar órdenes.

El endpoint recibe solo (además del header de idempotencia): userId, orderId.

userId se podría sacar el orderId, pero es consistente con todos los otros endpoints y nos ahorra un viaje a la base para buscar el id en base al orderId.

mismas validación de siempre de si el userId existe o no.
validación de si el orderId existe o no. Validación de si es un estado valido para cancelar (solo new por ahora).
Hay que validar pertenencia también, que al userId le pertenezca la orden que quiere cancelar.
Y reasonOF en la tabla historial debería ser "USER_REQUESTED"

Si se quiere cancelar una orden ya cancelada, estado no cancelable.

Para ser cohesivos con el endpoint de placement de orden, el endpoint va a ser un POST a /api/v1/users/{userId}/orders/{orderId}/cancel/

También es idempotente. También se lockea por usuario.
Y obviamente, se registra en la tabla de cambio de estado.

