# Refactors

Última pasada por el código, veo que hay para refactorizar y limpiar un poco.

1 - Corrección de ortografía en journals.

2 - Organización de paquetes. DTO se podría dividir en requests/responses.

3 - Order se podría reorganizar, un paquete obvio que sale a la vista es el de cancellation.

4 - OrderKind lo renombraria a "orderType" para ser coherentes con como lo menciona el enunciado

5 - RecordedTransition no se si es un objeto de modelo, me suena más a servicio.

6 - Revisar si quedó algun índice a agregar donde se busque o lea (creo que no pero repasar)



7a - tests: la carpeta de controller podría subdividirse por flujo
7b- idem persistencia

8 - CashTransferRequest no debería ser un record.

9 - OrderCancellationController tiene el path en el método. Que lo suba a nivel clase como el resto de los controllers.

10 - todos los controllers que terminan usando idempotencia tienen el mismo magic string "	private static final String IDEMPOTENCY_KEY = "Idempotency-Key";". Pasarlo a config, si cambia para uno cambia para todos.

11 - veo código repetido en "CostBasisWalk" accept y reduce son casi lo mismo. Hay que abstraer.
12 - mismo caso de mucho patrón de comportamiento repetido en "RequestedSubmission"

13 - En donde hay booleanos complejos y poco verbales (al estilo, "direction == CashMovement.CASH_OUT && snapshot.availableCash().compareTo(amount) < 0") extraer a una variable que diga sobre lo que se está decidiendo.

14a- revisar por que CashTransferKeySharedWithOrdersTest tarda tanto en correr (40s para un test es muchisimo)
