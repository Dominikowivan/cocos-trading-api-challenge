# Search

Repaso lo que ya escribí en un primer análisis.

Vamos a tomarlo como coincidencia parcial de texto, no como un fuzzy search.

Buscar por "banco" encuentra los activos "banco galicia" y "banco santander", pero "bnaco" no encuentra nada.

Hay que hacerlo fácilmente escalable porque son del tipo de endpoints a los cuales rápidamente se les agregan más filtros.
Hay que ver de normalizar el input, y qué hacer en caso de acentos o caracteres. Va a ser case y accent insensitive.

Ese y/o es otro caso de algo subjetivo. Puede indicar que se puede buscar con AND, o uno o el otro, o que podés elegir combinar con AND/OR los filtros.

Vamos a dejarlo que se comporte como un OR de los campos propuestos.

Devolvemos la variación diaria que se había nombrado en los reqs pero no decía dónde.

Y pesos o ARS no devuelve activo, no lo tomamos como tal, no es cohesivo con la decisión de la variación diaria y puede, asimismo, molestar futuros cambios.

El plan propuso traerse todos los instrumentos y filtrar en memoria. Ahí lo tuve que parar y decirle que haga el filtrado en la base de datos.
No es algo que escale: si crece mucho, como uno esperaría que crezca una lista de instrumentos, todos los usuarios van a estar trayéndose una cantidad grande de instrumentos
a la JVM y vamos a tener problemas de recursos. Que se encargue una base de datos, que está optimizada para buscar y filtrar.

Justo una base de datos SQL no es muy buena buscando por "parecido" o aplicando filtros. Para dos atributos que tomamos como AND está bien,
pero si sigue creciendo, considerar migrar la búsqueda a una base de datos con índices invertidos tipo Elasticsearch.

También agregué paginado, que era una consideración que no se estaba teniendo en cuenta. Pensando en esto ahora me doy cuenta de que no puse paginado
a la lista de activos del portafolio del usuario. Me lo anoto mentalmente para hacer ese refactor después de este feature.

Vamos a poner una paginación por defecto de 20 con máximo 100, pero configurable por query param para poder pedir de a más.
Ese dato por defecto, que sea configurable por env param.
Max query length 64.

Hacer una búsqueda de si quedó en algún lado otro "por defecto" configurable para pasar a env param. Lo dejo para el refactor del endpoint de portfolio.
Lo mismo, ver de poner validación al request de portfolio.

Falta revisar si falta agregar algún tipo de índice para mejorar la búsqueda.
Para eso, no sé si ticker debería ser unique. Frente a esa duda, prefiero no forzar una regla de negocio que desconozco; se consultaría a un experto de negocio/producto en un proyecto real.

Tasks e implement, revisamos después de eso.

Indefendible lo que produjo. Una query con múltiples folds, un choclazo que nadie puede leer ni entender.
Esto se dio por querer todo: unaccented, filtrando MONEDA, y juntando la data del día anterior.

Busqué un poco y hay un plugin para PostgreSQL que te permite llamar una función que saca acentos, pero prefiero no agregar dependencias
para solucionar una comodidad. Llegado a este punto, si queremos subir la complejidad de la búsqueda y mantener la complejidad baja, veo dos opciones:

1- Agregar una columna con el string en lowercase, sin acentos, sanitizado. Cuando se inserta en una columna, se denormaliza y se inserta en la otra, y buscás sobre eso.
2- Si sabés que ya vamos a ir a búsquedas más complejas, considerar una base de datos tipo Elasticsearch.

Y vamos a hacer lo siguiente: se divide en dos la query. Una busca el activo por texto, y la otra busca el rendimiento diario.
Son varias idas a la base de datos; pueden quedarse como repeatable read para que sean consistentes texto y precio, y también permite desacoplar
dos necesidades. No sobreoptimicemos pagando con gran complejidad cuando no hace falta.

La clase `DecimalPolicy` en el root me hace ruido. Para pensar también para el refactor después de esta feature.