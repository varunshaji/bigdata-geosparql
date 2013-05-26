Make use of JTS library's GeoSPatial query implementation and is plugged into bigdata as cuatom fucntions. THis simple code just
changes the BigdataSail class. And henece the nanosparql server could be made to support GeoSPARQL by just introducing thenew BigdataSail 
and the additional jars required ofr jts. Courtsey to useekm. I have referred their code and amended it to avoid the indexing part and
just included the geosparql functions. I have also changed the package structure from useekm opensahra to vss. It was done for coding 
easiness. I have done this purely for academic purpose....
