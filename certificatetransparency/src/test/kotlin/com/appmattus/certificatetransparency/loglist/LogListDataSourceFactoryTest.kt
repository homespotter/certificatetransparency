/*
 * Copyright 2021 Appmattus Limited
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

package com.appmattus.certificatetransparency.loglist

import com.appmattus.certificatetransparency.internal.loglist.LogListJsonFailedLoadingWithException
import com.appmattus.certificatetransparency.utils.TestData
import com.appmattus.certificatetransparency.utils.assertIsA
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

public class LogListDataSourceFactoryTest {

    init {
        Logger.getLogger(MockWebServer::class.java.name).level = Level.OFF
    }

    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {

            override fun dispatch(request: RecordedRequest): MockResponse {
                val response = when (request.path) {
                    "/log_list.json" -> MockResponse().setResponseCode(200)
                        .setBody(Buffer().write(TestData.file(TestData.TEST_LOG_LIST_JSON).readBytes()))
                    "/log_list.sig" -> MockResponse().setResponseCode(200)
                        .setBody(Buffer().write(TestData.file(TestData.TEST_LOG_LIST_SIG).readBytes()))
                    "/log_list.zip" -> MockResponse().setResponseCode(200)
                        .setBody(Buffer().write(TestData.file(TestData.TEST_LOG_LIST_ZIP).readBytes()))

                    else -> MockResponse().setResponseCode(404)
                }

                return response.apply(configuration)
            }
        }

        start()
    }

    private val baseUrl = server.url("/")

    private var configuration: MockResponse.() -> Unit = {}

    @Test
    public fun timeoutReturnsException() {
        runBlocking {
            // Given the log list service times out
            configuration = { setBodyDelay(5, TimeUnit.SECONDS) }
            val logListService = LogListDataSourceFactory.createLogListService(baseUrl = baseUrl.toString(), networkTimeoutSeconds = 1)

            // When we request the log list
            val dataSource = LogListDataSourceFactory.createDataSource(logListService = logListService)
            val result = dataSource.get()

            // Then a failure is returned with a SocketTimeoutException
            assertIsA<LogListJsonFailedLoadingWithException>(result)
            assertIsA<SocketTimeoutException>((result as LogListJsonFailedLoadingWithException).exception)
        }
    }

    @Test
    public fun successReturnsValid() {
        runBlocking {
            // Given a default log list service
            val logListService = LogListDataSourceFactory.createLogListService(baseUrl = baseUrl.toString())

            // When we request the log list
            val dataSource = LogListDataSourceFactory.createDataSource(logListService = logListService)
            val result = dataSource.get()

            println(result)

            // Then a valid result is returned
            assertIsA<LogListResult.Valid>(result)
        }
    }

    @Test
    public fun serverErrorReturnsException() {
        runBlocking {
            // Given the log list service times out
            configuration = {
                setResponseCode(500)
                setBody(Buffer())
            }

            val logListService = LogListDataSourceFactory.createLogListService(baseUrl = baseUrl.toString(), networkTimeoutSeconds = 1)

            // When we request the log list
            val dataSource = LogListDataSourceFactory.createDataSource(logListService = logListService)
            val result = dataSource.get()

            // Then a failure is returned with an IOException
            assertIsA<LogListJsonFailedLoadingWithException>(result)
            assertIsA<IOException>((result as LogListJsonFailedLoadingWithException).exception)
        }
    }
}
