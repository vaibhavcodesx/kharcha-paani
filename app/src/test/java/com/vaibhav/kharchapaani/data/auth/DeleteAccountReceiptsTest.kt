package com.vaibhav.kharchapaani.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeleteAccountReceiptsTest {
    @Test
    fun `all pages of my photos are deleted before the RPC`() = runBlocking {
        val photos = (1..201).map { "owner/tx-a/photo-$it.webp" } +
            (1..101).map { "owner/tx-${it.toString().padStart(3, '0')}/photo.webp" }
        val fake = FakeServer(photos)
        withRepository(fake) { deleteAccount(userId = "owner", keepName = true) }

        assertEquals(listOf("other/tx/keep.webp"), fake.objects.toList())
        assertEquals(listOf(100, 100, 100, 2), fake.batchSizes)
        assertEquals(listOf(true), fake.rpcCalls)
        assertTrue(fake.ownFolderWasEmptyAtRpc)
        assertTrue(fake.listRequests.any { (folder, offset) -> folder == "owner" && offset > 0 })
        assertTrue(fake.listRequests.any { (folder, offset) -> folder == "owner/tx-a" && offset > 0 })
    }

    @Test
    fun `an empty folder still lets the account be deleted`() = runBlocking {
        val fake = FakeServer(emptyList())
        withRepository(fake) { deleteAccount(userId = "owner", keepName = false) }

        assertTrue(fake.listRequests.isNotEmpty())
        assertTrue(fake.batchSizes.isEmpty())
        assertEquals(listOf(false), fake.rpcCalls)
    }

    @Test
    fun `a failed batch stops deletion and a retry removes the remaining photos`() = runBlocking {
        val fake = FakeServer((1..101).map { "owner/tx/photo-$it.webp" })
        fake.failDeleteBatch = 2
        withRepository(fake) {
            val error = runCatching { deleteAccount(userId = "owner", keepName = false) }.exceptionOrNull()
            assertTrue(error is RestException)
            assertTrue(fake.rpcCalls.isEmpty())
            assertEquals(1, fake.objects.count { it.startsWith("owner/") })

            fake.failDeleteBatch = null
            deleteAccount(userId = "owner", keepName = false)
        }

        assertTrue(fake.ownFolderWasEmptyAtRpc)
        assertEquals(listOf(false), fake.rpcCalls)
    }

    @Test
    fun `a network failure listing photos stops account deletion`() = runBlocking {
        val fake = FakeServer(listOf("owner/tx/photo.webp"))
        fake.listOffline = true
        withRepository(fake) {
            val error = runCatching { deleteAccount(userId = "owner", keepName = false) }.exceptionOrNull()
            assertTrue(error is IOException)
        }

        assertTrue(fake.rpcCalls.isEmpty())
        assertTrue(fake.batchSizes.isEmpty())
    }

    @Test
    fun `an apparently successful removal must leave the folder empty`() = runBlocking {
        val fake = FakeServer(listOf("owner/tx/photo.webp")).apply { ignoreDeletes = true }
        withRepository(fake) {
            val result = runCatching { deleteAccount(userId = "owner", keepName = false) }
            assertTrue(result.isFailure)
        }
        assertEquals(listOf(1), fake.batchSizes)
        assertTrue(fake.rpcCalls.isEmpty())
    }

    private suspend fun withRepository(fake: FakeServer, block: suspend AuthRepository.() -> Unit) {
        val client = fake.client()
        try {
            client.auth.importSession(
                UserSession(
                    accessToken = "test-session",
                    refreshToken = "test-refresh",
                    expiresIn = 3_600,
                    tokenType = "bearer",
                    user = UserInfo(id = "owner", aud = "authenticated"),
                ),
                autoRefresh = false,
            )
            AuthRepository(client, googleWebClientId = "").block()
        } finally {
            client.close()
        }
    }

    private class FakeServer(photos: List<String>) {
        val objects = (photos + "other/tx/keep.webp").toMutableSet()
        val listRequests = mutableListOf<Pair<String, Int>>()
        val batchSizes = mutableListOf<Int>()
        val rpcCalls = mutableListOf<Boolean>()
        var ownFolderWasEmptyAtRpc = false
        var failDeleteBatch: Int? = null
        var listOffline = false
        var ignoreDeletes = false

        fun client(): SupabaseClient = createSupabaseClient("https://local.invalid", "test-key") {
            httpEngine = OkHttp.create {
                config {
                    addInterceptor { chain ->
                        val request = chain.request()
                        val body = Buffer().also { request.body?.writeTo(it) }.readUtf8()
                        val input = Json.parseToJsonElement(body).jsonObject
                        val path = request.url.encodedPath
                        val (status, response) = when {
                            path.endsWith("/object/list/receipts") -> {
                                if (listOffline) throw IOException("test Storage network failure")
                                200 to list(input)
                            }
                            path.endsWith("/object/receipts") && request.method == "DELETE" -> delete(input)
                            path.endsWith("/rpc/delete_my_account") -> {
                                ownFolderWasEmptyAtRpc = objects.none { it.startsWith("owner/") }
                                rpcCalls += input.getValue("p_keep_name").jsonPrimitive.content.toBooleanStrict()
                                200 to "null"
                            }
                            else -> error("Unexpected request: $path")
                        }
                        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                            .code(status).message("Fake response")
                            .body(response.toResponseBody("application/json".toMediaType())).build()
                    }
                }
            }
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                autoSaveToStorage = false
                alwaysAutoRefresh = false
                enableLifecycleCallbacks = false
            }
            install(Storage)
            install(Postgrest)
        }

        private fun list(input: JsonObject): String {
            val folder = input.getValue("prefix").jsonPrimitive.content
            val limit = input.getValue("limit").jsonPrimitive.intOrNull ?: error("Missing limit")
            val offset = input["offset"]?.jsonPrimitive?.intOrNull ?: 0
            listRequests += folder to offset
            val prefix = "$folder/"
            return objects.asSequence().filter { it.startsWith(prefix) }
                .map { it.removePrefix(prefix).substringBefore('/') }.distinct().sorted()
                .drop(offset).take(limit).joinToString(prefix = "[", postfix = "]") { name ->
                    val id = if (folder == "owner") "null" else "\"test-file\""
                    """{"name":"$name","id":$id,"updated_at":null,"created_at":null,"last_accessed_at":null,"metadata":null}"""
                }
        }

        private fun delete(input: JsonObject): Pair<Int, String> {
            val paths = input.getValue("prefixes").jsonArray.map { it.jsonPrimitive.content }
            batchSizes += paths.size
            if (batchSizes.size == failDeleteBatch) {
                return 403 to """{"statusCode":"403","error":"Forbidden","message":"Fake Storage denial"}"""
            }
            if (!ignoreDeletes) objects.removeAll(paths.toSet())
            return 200 to "[]"
        }
    }
}
