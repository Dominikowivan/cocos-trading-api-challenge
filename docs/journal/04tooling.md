# Tooling

Bueno, ahora que tenemos un proyecto con primer feature implementado, vamos a hacernos cargo del tooling.

Quiero agregar:

# ArchUnit
Para que las reglas de arquitectura que establecimos se respeten.

# Formatter
Consistencia che, que corra un check en el CI de que todo lo que se pushea cumpla con el check.

# Sonar
Revision estatica de codigo y vulnerabilidades Para agregar al CI. No empecemos a acumular issues y deuda tecnica.

# Documentacion de la api
Estaba pensando swagger, pero si en el entregable damos un postman collection como pide el mail, esto no va a hacer falta.
De ser necesario en el futuro para comodidad de otros equipos, se agrega facilmente con anotaciones en el controller.
Lo que si: Deberiamos armar el postman collection directamente a partir de la especificacion openapi que generan los specs, y dejar listado
como parte del entregable de cada feature la modificacion de esa collection.

Vamos con esto, no paso por spec-kit por que son requerimientos tecnicos. Van a dejar adr seguro, y eso mas la intencion de este journal funcionan ya como suficiente documentacion.

# Metricas
TBD