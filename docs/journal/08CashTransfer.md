# TRANSFERENCIA CASH

Un solo endpoint que recibe:

userID, side (cash_IN o cash_out), y cantidad.

CASH_IN no se valida contra nada, no se contempla de donde sale el dinero. Todavia no existe el concepto de transferencias entre usuarios o movimientos entre cuentas, asi mismo que no entra en el scope del ejercicio las cuentas bancarias tampoco.

CASH_OUT si, tiene que validar que el usuario tenga suficiente "MONEDA" disponible. Si no la tiene, la orden queda rejected.

Idempotencia va tambien por que es seria un endpoint con side effect.
UserLock tambien por que afectaria multiples ordenes en paralelo.
Primero se hace el endpoint.
Despues  se pone el claim de idempotencia + lockeo de usuario, siguiendo el mismo patron que hay OrderSubmissionService.
Y despues ABSTRAEMOS la logica duplicada de claim de idempotencia y lockeo de usuario que seguro seguro va a haber, y va a ser mucho mas limpio que viva en su propia abstraccion (seguro un service propio).