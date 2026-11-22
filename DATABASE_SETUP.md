# Local PostgreSQL setup

This project runs Spring Boot on your computer and PostgreSQL 17 in Docker. Run the commands below from the repository root, where `pom.xml` and `docker-compose.yaml` live.

## 1. Prerequisites

- Docker Desktop installed and running, with `docker compose` available.
- JDK 21 selected for the application and Maven. Check `java -version` and `./mvnw -version`. IntelliJ's JDK and your terminal's JDK can differ.
- Internet access on the first run to download the PostgreSQL image and Maven dependencies. Maven itself is supplied by the Maven wrapper (`./mvnw`).

## 2. Set the local password

Create a file named `.env` in the repository root with one line:

```dotenv
POSTGRES_PASSWORD=replace_with_your_local_password
```

Replace the example value before starting the database. Use an unquoted password made of letters and digits for this shared file; Java properties and Docker dotenv files handle quotes, backslashes, and interpolation differently. A random hexadecimal password is a simple compatible choice.

If `.env` already exists, keep its password when reconnecting to the existing database. Do not overwrite it with the example above. `.gitignore` already excludes `.env`; never commit it or copy the real password into this guide.

The two processes read the same file separately:

- Docker Compose automatically reads `.env` and substitutes `${POSTGRES_PASSWORD}` in `docker-compose.yaml`.
- Spring Boot does **not** automatically load dotenv files. This project's `spring.config.import=optional:file:.env[.properties]` explicitly imports it as a Java properties file. The `[.properties]` hint tells Spring how to parse the filename. The datasource then resolves `spring.datasource.password=${POSTGRES_PASSWORD}` from configuration.

`optional:` allows the file to be absent when the password is supplied through an actual environment variable. It does not provide a default password. An exported `POSTGRES_PASSWORD` takes precedence over the imported file, so avoid accidentally setting an old value in your terminal or IntelliJ run configuration.

## 3. Start PostgreSQL

Validate the Compose file before starting containers:

```sh
docker compose config --quiet
```

The flag is `--quiet`, not `--quite`. No output and an exit status of zero mean the configuration is valid. This checks the Compose configuration, not database connectivity or whether the password matches an existing database. Without `--quiet`, `docker compose config` prints the resolved configuration, which can include the password.

Start the database in the background and check it:

```sh
docker compose up -d db
docker compose ps
docker compose exec -T db pg_isready -U engineer_user -d engineer_directory
```

`-d` means detached mode: the container keeps running after the command returns. `docker compose up -d` also works; it starts all services in the file, which currently contains only `db`.

On the first run, expect Docker to pull the image and create the project network, named volume, and container. Later runs normally reuse them. In `docker compose ps`, expect `postgres-engineer-directory` to be `Up` with `127.0.0.1:5332->5432/tcp`. `Up` means the container is running; the readiness check below confirms PostgreSQL is accepting connections.

Wait until the readiness command says `accepting connections`. If startup fails, inspect:

```sh
docker compose logs --tail=50 db
```

The Compose configuration supplies these initial settings:

| Setting | Value | Meaning |
| --- | --- | --- |
| Image | `postgres:17` | PostgreSQL server version |
| Service / container | `db` / `postgres-engineer-directory` | Compose service name / Docker container name |
| Database | `engineer_directory` | Database created on the first initialization |
| User | `engineer_user` | Initial PostgreSQL role; the image creates it as a superuser for this local setup |
| Host address | `127.0.0.1:5332` | Connection address for Spring Boot, IntelliJ, or another tool on your computer |
| Container port | `5432` | PostgreSQL's port inside Docker |
| Named volume | `postgres_data` | Stores database files outside the container; Docker usually prefixes its actual name with the Compose project name |

The mapping `127.0.0.1:5332:5432` forwards your computer's port 5332 to the container's port 5432 and binds it to the local loopback address.

`POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` initialize an **empty** data directory. Reusing the volume skips that initialization. Editing `.env` and restarting Docker does not change the password of an existing database role.

## 4. Verify database access

Check the selected database and role:

```sh
docker compose exec -T db psql -U engineer_user -d engineer_directory \
  -v ON_ERROR_STOP=1 -c 'SELECT current_database(), current_user;'
```

Expect `engineer_directory` and `engineer_user`. This command uses a Unix socket inside the container; it checks that SQL works, but does not prove that the application's password works over TCP.

For a password-authenticated TCP check inside the container:

```sh
docker compose exec db psql -h 127.0.0.1 -p 5432 \
  -U engineer_user -d engineer_directory -W \
  -c 'SELECT current_database(), current_user;'
```

Enter the password from `.env` at the hidden prompt. With a locally installed `psql` client, test the host port using the same command with `-p 5332` instead, omitting `docker compose exec db`.

For IntelliJ's database tool or another database client, use host `127.0.0.1`, port `5332`, database `engineer_directory`, user `engineer_user`, and the password from `.env`.

### Explore the database interactively

If `psql` is installed on your computer, connect through the same host and port as Spring Boot:

```sh
psql -h 127.0.0.1 -p 5332 -U engineer_user -d engineer_directory
```

Enter the password from `.env` when prompted. At the `engineer_directory=#` prompt, run:

```sql
SELECT current_database();
SELECT current_user;
```

Expect `engineer_directory` and `engineer_user`, respectively. Successful queries after password authentication verify that the host port, database, role, and password work together.

These additional commands belong to the `psql` client and do not need semicolons:

```text
\dt
\l
\q
```

`\dt` lists tables visible through the current schema search path, `\l` lists databases, and `\q` exits. `Did not find any relations` from `\dt` is expected for this project's initial state: no tables have been created, and `SoftwareEngineer` is not yet a JPA entity. It does not mean the connection failed.

### If the local psql client is older than the server

The initial setup used a Homebrew `psql` 16.11 client against PostgreSQL server 17.12. Basic queries succeeded, but `\l` failed with:

```text
ERROR: column d.daticulocale does not exist
HINT: Perhaps you meant to reference the column "d.datlocale".
```

This is a client/server compatibility issue: backslash commands generate queries against PostgreSQL's system catalogs, which can change between major versions. An older client can therefore connect successfully while some of its commands fail. PostgreSQL's [psql compatibility guidance](https://www.postgresql.org/docs/17/app-psql.html#APP-PSQL-NOTES) recommends using a client of the same or a newer major version than the server.

The simplest option here is to use the PostgreSQL 17 client already included in the database container. Exit the local session with `\q`, then run:

```sh
docker compose exec db psql -U engineer_user -d engineer_directory
```

Try `\l` and `\dt` again. This session uses the container's local Unix socket, so it is useful for inspecting the database but does not test the host's port mapping or password authentication. Alternatively, install/select a local PostgreSQL 17 client and check `psql --version` before reconnecting through port 5332. No database reset or schema change is needed to fix this client mismatch.

## 5. Start Spring Boot

The datasource configuration is:

```properties
spring.config.import=optional:file:.env[.properties]
spring.datasource.url=jdbc:postgresql://127.0.0.1:5332/engineer_directory
spring.datasource.username=engineer_user
spring.datasource.password=${POSTGRES_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver
```

Run `EngineerDirectoryServiceApplication` in IntelliJ with JDK 21 and the **repository root as the working directory**, or run:

```sh
./mvnw spring-boot:run
```

No dotenv plugin or extra dependency is needed. The import is relative to the process working directory, not to `src/main/resources`. Restart the application after changing its configuration.

The JDBC URL tells the PostgreSQL JDBC driver where to connect. Spring Boot configures a datasource and HikariCP connection pool from these properties; Hibernate uses that datasource for JPA. Hibernate detects the standard PostgreSQL dialect from database metadata, so an explicit `PostgresPlusDialect` is unnecessary and targets a different PostgreSQL variant.

Look for `HikariPool-1 - Start completed` and successful application startup. While the application is running, check its database sessions:

```sh
docker compose exec -T db psql -U engineer_user -d engineer_directory \
  -c "SELECT application_name, client_addr, state, count(*) FROM pg_stat_activity WHERE datname = current_database() GROUP BY application_name, client_addr, state;"
```

`PostgreSQL JDBC Driver` sessions show that a JDBC client has connected. Multiple idle sessions are normal for a connection pool. Match them to the running application's sockets when more than one Java client uses the database.

**Current project limitation:** `GET /api/v1/software-engineers` returns a Java `List.of(...)` directly. `SoftwareEngineer` is not yet a JPA entity and there is no repository/service persistence flow. A successful HTTP response therefore does not verify a database query or saved engineer records.

## 6. Understand data persistence before restarting

The current application has:

```properties
spring.jpa.hibernate.ddl-auto=create-drop
```

Hibernate can drop and recreate tables mapped by JPA entities at startup, then drop them again on a clean shutdown. This is useful for disposable tutorial data. The Docker volume cannot protect records from SQL that deletes their tables. There are currently no entity mappings in this project, but this setting matters as soon as you add them.

For a learning setup where you want entity data to survive application restarts, you can deliberately change it to `update`; Hibernate will attempt to adjust mapped tables without recreating them each time. This is not a substitute for reviewed database migrations in a production application. `none` disables Hibernate schema changes, and `validate` checks existing tables without creating them.

The existing `@SpringBootTest` also starts the application context against the configured database. To run the current smoke test without Hibernate changing tables:

```sh
./mvnw -Dspring.jpa.hibernate.ddl-auto=none test
```

Stop and restart the database while retaining its volume:

```sh
docker compose stop db
docker compose start db
```

`docker compose down` also retains the named volume by default. **Do not add `-v` unless you intentionally want to delete the local database and all its contents.**

## 7. Common problems

| Symptom | What to check |
| --- | --- |
| `unknown flag: --quite` | Use `docker compose config --quiet`; successful validation prints nothing. |
| Cannot connect to the Docker daemon | Start Docker Desktop and wait for its engine to become ready. |
| Connection refused on port 5332 | Check `docker compose ps`, readiness, and the host port mapping. |
| Port 5332 is already in use | Find the process using it, or deliberately change both the Compose host port and the JDBC URL. |
| Missing `POSTGRES_PASSWORD` / unresolved placeholder | Check that `.env` exists in the working directory and has the exact key. Alternatively supply a real environment variable. |
| Password authentication failed | Compare `.env` with the password used when the volume was first initialized. Check for an overriding environment variable. Restarting the container does not reset the role's password. |
| Need to change an existing role's password | Open `docker compose exec db psql -U engineer_user -d engineer_directory`, run `\password engineer_user`, then update `.env` to match and restart Spring Boot. The interactive prompt avoids putting the new password in SQL command history. |
| Relation/table does not exist | Check entity annotations and the schema setting. A plain Java class does not create a table. |
| `\dt` says `Did not find any relations` | No tables are visible in the current schema search path. This is expected before adding entities or creating tables. |
| `\l` fails with `d.daticulocale does not exist` | Check for a psql 16 client connecting to PostgreSQL 17. Use the container's psql 17 client or select a compatible local client. |
| Data disappears after restarting Spring Boot | Check `ddl-auto=create-drop`; a persistent Docker volume does not override Hibernate. |
| Maven reports an unsupported Java release | Select JDK 21 for Maven as well as for the application. Check `./mvnw -version`. |

## References

- [Spring Boot external configuration, placeholders, and file imports](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Spring Boot database initialization and Hibernate schema settings](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [Official PostgreSQL Docker image and initialization variables](https://hub.docker.com/_/postgres)
- [Hibernate PostgresPlusDialect](https://docs.hibernate.org/orm/current/javadocs/org/hibernate/dialect/PostgresPlusDialect.html)
- [Docker Compose configuration validation](https://docs.docker.com/reference/cli/docker/compose/config/)
- [psql commands and client/server compatibility](https://www.postgresql.org/docs/17/app-psql.html)
