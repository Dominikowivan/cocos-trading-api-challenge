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


