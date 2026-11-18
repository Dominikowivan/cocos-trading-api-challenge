# Tooling

Bueno, ahora que tenemos un proyecto con primer feature implementado, vamos a hacernos cargo del tooling.

Quiero agregar:

# ArchUnit
Para que las reglas de arquitectura que establecimos se respeten.

# Formatter
Consistencia che, que corra un check en el CI de que todo lo que se pushea cumpla con el check.
Vamos a ir con el Spring Java Format. El proyecto es principalmente Spring Boot, y deliberadamente seguimos las versiones
que van en el BOM. Pienso que es lo más consistente y coherente mantenernos en el framework.

# Sonar
Revisión estática de código para agregar al CI. No empecemos a acumular issues y deuda técnica.
¡Perfecto! Hubo que tunear un par de configs, pero salió andando y encontró 4 issues con los que estoy de acuerdo. Fixed y pusheados.

# Análisis de vulnerabilidades - Trivy
Sonar ya cubre vulnerabilidades de código y estáticas del proyecto, pero falta vulnerabilidades de dependencias/librerías.
Agregué Trivy para levantar CVEs.
Esto corre como parte del proceso de build en el CI, pero no nos defiende si algo que ya está en producción es vulnerado.
Para eso necesitaríamos una herramienta de análisis y alerta tipo Dependabot, pero como esto no va a vivir realmente en producción
y tampoco quiero las alertas siendo mandadas a mi mail de un proyecto que se entrega y muere, no lo voy a agregar.

# Documentación de la API
Estaba pensando Swagger, pero si en el entregable damos un Postman collection como pide el mail, esto no va a hacer falta.
De ser necesario en el futuro para comodidad de otros equipos, se agrega fácilmente con anotaciones en el controller.
Lo que sí: deberíamos armar el Postman collection directamente a partir de la especificación OpenAPI que generan los specs, y dejar listado
como parte del entregable de cada feature la modificación de esa collection.

Dejamos como contrato el OpenAPI configurado en docs/api, y agregamos una librería para verificar que la estructura de lo que está en esa documentación
es siempre válida.

Vamos con esto, no paso por Spec Kit porque son requerimientos técnicos. Van a dejar ADR seguro, y eso más la intención de este journal funcionan ya como suficiente documentación.

# Dockerfile
Sumamos un Dockerfile para portabilidad y que sea fácil levantar la app con solo tener Docker.

# Métricas
Vamos a sumar Actuator de Spring, que ya nos da health y trae Micrometer, que mide las golden signals clásicas.
Vamos a exponer con formato para Prometheus, pero de ser necesario usar otra herramienta de observabilidad, se
cambia fácilmente con una configuración y poniendo la dependencia del registry correspondiente.
No voy a instalar Prometheus para este ejemplito, la herramienta de observabilidad suele ser cross-apis, no tiene sentido que viva como parte de este proyecto. Sin contar que si se cae la api, se cae Prometheus también.
