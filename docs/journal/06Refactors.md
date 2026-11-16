# Refactors

Desde que se pusheó la última feature a develop, salieron unos nuevos issues de Sonar con los que estoy, en su mayoría, de acuerdo.
(Aclaro que Sonar, en el free tier, solo corre en develop; por eso solo lo puedo revisar una vez que ya está subido ahí).

Sumado a eso, hay un par de cuestiones que quiero atacar:

1. ¿Me faltó algún índice en los últimos desarrollos?
2. ¿Hay constraints para agregar a la base que podrían ayudar a consolidar la consistencia en base a nuestras asunciones de negocio?
3. Hacer configurable el pool de Hikari y el timeout a la base de datos. ¿Vale la pena?
4. Hay que agregar paginación a los activos del endpoint de portfolio.
5. Issues de Sonar.

Bueno, vamos uno por uno.

- **1.** Repasé; por ahora, no.
- **2.** Sí. Agregué más que nada `NOT NULL` a columnas en las que no tiene sentido tener `null` y que siempre tenían datos en el seed.
- **3.a.** Timeout hacia la base de datos puesto en 10 segundos. Decisión basada en dar colchón y en que esos segundos sirvan principalmente para cortar un request colgado. Habría que tener bien claros la cantidad de usuarios, la cantidad de datos en la base y los SLAs para ser precisos a la hora de elegir en cuántos segundos cortar.
- **3.b.** Con las conexiones de Hikari, lo mismo: vamos a dejar el default, pero dejemos el default expuesto como configuración, así se entiende que es una decisión tomada con consciencia.
- **5.** Vamos primero con los issues de Sonar, que hay uno respecto a la paginación y no queremos pisar dos veces el mismo palito.
- **4.** Ahora sí. Pero haciéndolo me di cuenta de que no tiene sentido: ya nos traemos todas las órdenes a memoria por diseño.
  Lo que sí podemos hacer es reemplazar la paginación custom que pusimos por el `Pageable` de Spring. No habia necesidad desde un principio en una implementacion propia.
