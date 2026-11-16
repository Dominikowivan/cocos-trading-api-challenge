# Refactors

Desde que se pusheo la ultima feature a develop, salieron unos nuevos issues de Sonar con los que estoy en su mayoria de acuerdo.
(Aclaro que sonar en free tier solo corre en develop, por eso solo lo puedo revisar una vez ya esta subido ahi)
Sumado a eso, hay un par de cuestiones que quiero atacar:
1- Me falto algun indice en los ultimos desarollos?
2- Hay constrains para agregar a la base que podrian ayudar a consolidar la consistencia en base a nuestras asumsiones de negocio?
3- configurable el pool de hikari y timeout a la base de datos. Vale la pena?
4- Hay que agregar paginacion a los activos del endpoint de portfolio
5- issues de sonar

Bueno, vamos uno por uno.
1- repase, por ahora no.
2- Si, agregue mas que nada not nulls a columnas que no tiene sentido tener null y que siempre tenian datos en el seed.
3.a- Timeout hacia la base de datos puesto en 10 segundos. Decision basada en dar colchon y que esos segundos sirvan principalmente
para cortar un request colgado. Habria que tener bien claros cantidad de usuarios, cantidad de data en la base datos y SLAs para ser precisos
a la hora de elegir en cuantos segundos cortar
3.b- Las conecciones hikari lo mismo, vamos a dejar el default, pero dejemos el default expuesto como configuracion, asi se entiende que es una decision tomada con consciencia.
5- vamos primero con los issues de sonnar que hay uno respecto a la paginacion y no queremos pisar dos veces el mismo palito.
