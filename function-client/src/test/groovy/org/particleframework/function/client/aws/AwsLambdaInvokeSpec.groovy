/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.particleframework.function.client.aws

import io.reactivex.Single
import org.particleframework.context.ApplicationContext
import org.particleframework.core.type.Argument
import org.particleframework.function.client.FunctionClient
import org.particleframework.function.client.FunctionDefinition
import org.particleframework.function.client.FunctionInvoker
import org.particleframework.function.client.FunctionInvokerChooser
import org.particleframework.http.annotation.Body
import spock.lang.IgnoreIf
import spock.lang.Specification

import javax.inject.Named

/**
 * @author graemerocher
 * @since 1.0
 */
@IgnoreIf({
    return !new File("${System.getProperty("user.home")}/.aws/credentials").exists()
})
class AwsLambdaInvokeSpec extends Specification {


    void "test setup function definitions"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'aws.lambda.functions.test.functionName':'particle-function',
                'aws.lambda.functions.test.qualifier':'something'
        )
        
        Collection<FunctionDefinition> definitions = applicationContext.getBeansOfType(FunctionDefinition)
        
        expect:
        definitions.size() == 1
        definitions.first() instanceof AWSInvokeRequestDefinition
        definitions.first().invokeRequest.functionName == 'particle-function'
        definitions.first().invokeRequest.qualifier == 'something'

    }

    void "test setup lambda config"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'aws.lambda.functions.test.functionName':'particle-function',
                'aws.lambda.functions.test.qualifier':'something',
                'aws.lambda.region':'us-east-1'
        )
        AWSLambdaConfiguration configuration = applicationContext.getBean(AWSLambdaConfiguration)

        expect:
        configuration.builder.region == 'us-east-1'
    }

    void "test invoke function"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'aws.lambda.functions.test.functionName':'particle-function',
                'aws.lambda.region':'us-east-1'
        )

        when:
        FunctionDefinition definition = applicationContext.getBean(FunctionDefinition)
        FunctionInvokerChooser chooser = applicationContext.getBean(FunctionInvokerChooser)
        Optional<FunctionInvoker> invoker = chooser.choose(definition)

        then:
        invoker.isPresent()

        when:
        FunctionInvoker invokerInstance = invoker.get()

        Book book = invokerInstance.invoke(definition, new Book(title: "The Stand"), Argument.of(Book))

        then:
        book != null
        book.title == "THE STAND"
    }


    void "test invoke client with @FunctionClient"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'aws.lambda.functions.test.functionName':'particle-function',
                'aws.lambda.region':'us-east-1'
        )

        when:
        MyClient myClient = applicationContext.getBean(MyClient)
        Book book = myClient.particleFunction( new Book(title: "The Stand") )

        then:
        book != null
        book.title == "THE STAND"

        when:
        book = myClient.someOtherName( "The Stand" )

        then:
        book != null
        book.title == "THE STAND"

        when:
        book = myClient.reactiveInvoke( "The Stand" ).blockingGet()

        then:
        book != null
        book.title == "THE STAND"
    }

    static class Book {
        String title
    }

    @FunctionClient
    static interface MyClient {
        Book particleFunction(@Body Book book)

        @Named('particle-function')
        Book someOtherName(String title)

        @Named('particle-function')
        Single<Book> reactiveInvoke(String title)
    }
}
