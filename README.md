# diaschema

A clojure library to read database (Postgresql and Mysql) schemas and generate diagrams.

## Usage

Environment variables supports:
1. `DB_TYPE`, defaults to be `postgresql`
2. `DB_USER`
3. `DB_PASSWD`
4. `DB_HOST`, defaults to be `127.0.0.1`
5. `DB_NAMES`, multiple dbs should be seperated by `,`

``` sh
db_names=db1,db2 db_user=your_user_name output_dir=./db_dia java -jar target/diaschema-0.1.0-SNAPSHOT-standalone.jar
```

## License

Copyright © 2018 tiensonqin@gmail.com

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
