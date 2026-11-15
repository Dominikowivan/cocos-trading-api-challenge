# Setup

Vamos a arrancar con spring initializer que ya me da la base del proyectito armada con la mayoria de las cosas que necesito.
- Spring web (para levantar la api)
- Spring data jpa (Anotaciones y ORM)
- Spring Flyway (Migraciones)
- spring-boot-starter-validation (Validaciones por anotación en dto/controllers)
- Test containers

Voy con migraciones de flyway porque pienso hacer cambios en la base de datos que se me dio, y me parece la forma más ordenada de dejar documentadas y aplicar esos cambios sin que tenga que poner mano yo y potencialmente equivocarme.
Voy con test containers para testear y correr local con lo más parecido a lo que va a ser la base de datos "productiva" que es la que se me dio.

Esa base de datos la voy a tomar como si fuera una base de datos de "staging/uat/preprod", no es un lugar para experimentar ni hacer cambios, es una base a la cual van los cambios listos para salir a producción.

Ya con el spring initializer arrancado, creo el repo de git, git ignore, pongo una IA a armarme el readme, y del repo del challenge me descargo database.sql para copiarme el seed y armarme la misma base local.

También, dejo configurada la versión de postgresql que usa "staging" que es 18.7 así usamos la misma local y para desarrollo.

Que corran los tests, que levante la app.

Sumo github actions y jacoco para tener un CI con coverage.
Vamos a poner a una IA a leer mi journal, derivar los ADR de mi intención, confirmar que está todo lo que quiero, y hacemos el primer commit.

Primer commit y CI en verde listos!

Agregamos ahora spec kit para trabajar con SDD.
Elijo trabajar con SDD como estructura que permita a todo desarrollador que vaya a trabajar en este proyecto usar la inteligencia artificial
bajo las mismas condiciones que todos.
Los mismos skills, los mismos supuestos explícitos para todos, documentados.

Aun así, eso no significa que vaya a hacer todo con SDD. Cambios pequeños o que no necesitan inteligencia artificial los hago a mano o sin pasar por el flujo de SDD y listo.
Para esos cambios, la documentación es el mismo código y versionado de git.

Arranco ahora con la constitución.

La construyo en base a lo anotado en el journal y a lo que tenía en los ADR.
Agregué dos principios más que no estaban anotados en ningún lado pero ya tenía en mente:

Quiero trabajar con una arquitectura hexagonal de puertos y adaptadores (No hace falta dividir todo en usecases). Voy a hacer esto porque, a medida que el proyecto escala, es importante poder mantenerlo organizado y fácil de recorrer. Si viste una arquitectura hexagonal, viste todas,
te da el beneficio de saber cómo ubicarte bien en cualquier proyecto que sea hexagonal. Y principalmente, la división en capas, que permite que el código sea extensible y escalable, que sea fácil de intercambiar lo que esté detrás de cada interfaz, y que no haya acoplamiento entre capas que no deberían tener nada que ver.

Ese, y el principio de diseñar siempre pensando en la extensibilidad y en la lectura humana.

Vamos a commitear y vamos al primer feature.
