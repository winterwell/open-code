
# DataLog

https://lg.good-loop.com/

DataLog is a flexible store for numerical data.

It can use ES, SQL, or csv as it's backend. We use ES.

Data is organised by "dataspace", e.g. `gl`, `green` and `emissions` are the main dataspaces.

Key classes:

- DataLogEvent
- DataLogServer
- DataServlet - report on data & query params process
- LgServlet - log data
- ESStorage

# Docs

Wiki: https://wiki.good-loop.com/books/in-house-services/page/overview-jvj

Auto-generated Java docs can be found at: https://java-doc.good-loop.com/datalog/

