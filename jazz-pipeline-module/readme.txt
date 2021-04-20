# compile groovy recursively:
groovyc **/*.groovy

# inside jazz_jar folder run:
jar cvf util.jar -C . .

# usage of jar
groovy -cp util.jar test.groovy
