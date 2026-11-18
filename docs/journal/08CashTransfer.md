# TRANSFERENCIA CASH

Un solo endpoint que recibe:

userID, side (cash_IN o cash_out), y cantidad.

CASH_IN no se valida contra nada, no se contempla de dónde sale el dinero. Todavía no existe el concepto de transferencias entre usuarios o movimientos entre cuentas, así mismo que no entra en el scope del ejercicio las cuentas bancarias tampoco.

CASH_OUT sí, tiene que validar que el usuario tenga suficiente "MONEDA" disponible. Si no la tiene, la orden queda rejected.

Idempotencia va también porque es seria un endpoint con side effect.
UserLock también porque afectaría múltiples órdenes en paralelo.
Primero se hace el endpoint.
Después se pone el claim de idempotencia + lockeo de usuario, siguiendo el mismo patrón que hay OrderSubmissionService.
Y después ABSTRAEMOS la lógica duplicada de claim de idempotencia y lockeo de usuario que seguro seguro va a haber, y va a ser mucho más limpio que viva en su propia abstracción (seguro un service propio).