# Search

Repaso lo que ya escribi en un primer analisis.

Vamos a tomarlo como coincidencia parcial de texto, no como un fuzzy search.

buscar por "banco" encuentra los activos "banco galicia" y "banco santander", pero "bnaco" no encuentra nada.

Hay que hacerlo fácilmente escalable porque son del tipo de endpoints a los cuales rápidamente se les agregan más filtros.
Hay que ver de normalizar el input, y qué hacer en caso de acentos o caracteres. Va a ser case y accent insensitive.

Ese y/o es otro caso de algo subjetivo. Puede indicar que se puede buscar con AND, o uno o el otro, o que podés elegir combinar con AND/OR los filtros.

Vamos a dejarlo como un AND implícito: todos los filtros que se agreguen se matchean como AND. Y dejar pendiente una mejora del filtro para soportar OR.

Devolvemos la variacion diaria que se habia nombrado en los reqs pero no decia donde.

Y Pesos o ars no devuelve activo, no lo tomamos como tal, no es cohesivo con la decision de la variacion diaria y puede quedar asi mismo molestar futuros cambios.

El plan propuso traerse todos los instrumentos y filtrar en memoria. Ahi lo tuve que parar y decirle que haga el filtrado en la base de datos.
No es algo que escale, si crece mucho como uno espearia que cresca una lista de instrumentos, todos los usuarios van a estar trayendose una cantidad grande de instrumentos
a la jvm y vamos a tener problemas de recursos. Que se encargue una base de datos que esta optimisada para busquedas y filtrar.

Justo una base de datos sql no es muy buena buscando por "parecido" o aplicando filtros. Para dos atributos que tomamos como AND esta bien
pero si sigue creciendo, considerar migrar la busqueda a una base de datos con indices invertidos tipo ElasticSearch.

Tambien agregue paginado que era una consideracion que no se estaba teniendo en cuenta. Pensando en esto ahora me doy cuenta que no puse paginado
a la lista de activos del portafolio del usuario. Me lo anoto mentalmente para hacer ese refactor despues de este feature.

Vamos a poner una paginacion por defecto de 20 con maximo 100, pero configurable por query param para poder pedir de a mas.
Ese dato por defecto que sea configurable por env param.
Max query lenght 64.

Hacer una busqueda si quedo en algun lado otro "por defecto" configurable para pasar a env param. Lo dejo para el refactor del endpoint de portfolio.
Lo mismo, ver de poner validacion al request de de portfolio.

Falta revisar si falta agregar algun tipo de indice para mejorar la busqueda.
Para eso, no se si tickername deberia ser unique. Frente a esa duda, prefiero no forzar una regla de negocio que desconozco, se consultaria a un experto de negocio/producto en un proyecto real.

