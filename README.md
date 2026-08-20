# MultiVoteListener

*MultiVoteListener* is a leightweight listener for Votifier that has been inspired by the Simple Vote Listener. However it adds a couple of features that are required on a server I am working on as an Admin.

The current version of MultiVoteListener comes with the following features:

- Handle multiple vote services independently
- Vault support
- PlayerPoints support
- Run commands through console upon vote
- Run commands through console if player who voted is online
- Customizable messages & broadcasts
- Color code support for messages
- Variables in commands & messages
- ClickEvent on broadcast message - opens vote (or any other valid) URL on player click (This feature is only supported when running a Spigot Server)
- Block unknown player votes
- Enable / disable selected services
- Default service (if no configured service matches an incoming vote)

## Build & Install

*Requires:* 	 Votifier, Vault  
*Can hook into:* PlayerPoints

Build with Maven:

```bash
mvn clean package
```

The packaged plugin jar will include the required database libraries for HikariCP and MySQL.


## Configuration

The plugin uses a HikariCP connection pool. Configure the database section in `config.yml`:

```yml
database:
  jdbcUrl: "jdbc:mysql://127.0.0.1:3306/blockminers_server?useSSL=false&serverTimezone=UTC&tcpKeepAlive=true&connectTimeout=5000&socketTimeout=10000"
  username: "root"
  password: "test"
  hikari:
	poolName: "MultiVoteListenerPool"
	maximumPoolSize: 10
	minimumIdle: 2
	connectionTimeout: 5000
	idleTimeout: 600000
	maxLifetime: 1800000
	keepaliveTime: 300000
	validationTimeout: 3000
	initializationFailTimeout: 1
	leakDetectionThreshold: 0
	autoCommit: true
	connectionTestQuery: "SELECT 1"
```

If the pool cannot be initialized during plugin startup, the plugin disables itself and writes an error to the console.

## Support & Troubleshooting
Need help? Have a feature request?

Feel free to contact me via email, PN, forum, etc,. Since the plugin is running on my server as well I am very motivated to get errors fixed immediatelly once they come to my attention.

Implementation of new features depend on how complex, popular and challenging such a request is. In most cases I will have a look into it and try to provide the requested feature.

