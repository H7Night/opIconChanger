package com.opiconchanger.utils

import java.util.concurrent.TimeUnit

/**
 * 统一的 root/su 命令执行器。
 *
 * 设计要点：
 * - 全部走数组式 exec，捕获 stdout/stderr，带超时与退出码；
 * - 单条命令由调用方组装（含 RootExec.shQuote 引用），内部不拼接 shell 字符串；
 * - rootAvailable() 探测集中在此，避免各处重复探测。
 */
object RootExec {
    private const val DEFAULT_TIMEOUT_MS = 10_000L

    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val succeeded: Boolean get() = exitCode == 0
    }

    /** 以 root 执行一条 shell 命令。命令中的参数需先用 [shQuote] 引用。 */
    fun exec(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Result {
        if (command.isBlank()) return Result(-1, "", "empty command")
        return run(arrayOf("su", "-c", command), timeoutMs)
    }

    /** 直接执行（无需 root），参数数组形式，无 shell 注入面。 */
    fun execDirect(vararg args: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Result =
        run(arrayOf(*args), timeoutMs)

    fun rootAvailable(): Boolean = exec("echo ok").succeeded

    /**
     * 为 shell 命令安全引用单个参数（单引号包裹，内部单引号转义）。
     * 示例: shQuote("/data/a b/c") -> '/data/a b/c'
     */
    fun shQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    private fun run(args: Array<String>, timeoutMs: Long): Result {
        return try {
            val p = Runtime.getRuntime().exec(args)
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val t1 = Thread {
                p.inputStream.bufferedReader().use { r -> r.forEachLine { stdout.append(it).append('\n') } }
            }.apply { isDaemon = true; start() }
            val t2 = Thread {
                p.errorStream.bufferedReader().use { r -> r.forEachLine { stderr.append(it).append('\n') } }
            }.apply { isDaemon = true; start() }

            val finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                p.destroy()
                return Result(-1, stdout.toString(), "timeout after ${timeoutMs}ms")
            }
            t1.join(500)
            t2.join(500)
            Result(p.exitValue(), stdout.toString(), stderr.toString())
        } catch (e: Exception) {
            Result(-1, "", "${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
