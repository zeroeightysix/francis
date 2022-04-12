# francis

My minecraft bot (accompanied by a live chat feed on discord) made for [simpvp.net](https://simplicitypvp.net/w/Main_Page)

It is (as of writing) composed of three microservices that communicate using [RabbitMQ](https://www.rabbitmq.com/), using a shared [MariaDB](https://mariadb.org/) database.

In case you want to host your own instance (*please don't, or at least not on simpvp*), configuration is done through a `config.json` file in each of the services' working directories.
I'm sure you can figure out what it needs to contain by yourself. It's in the code, anyways.

Check out the [command reference](https://gist.github.com/zeroeightysix/74165b5a6cba1d1a37e30bd72158f54e)
