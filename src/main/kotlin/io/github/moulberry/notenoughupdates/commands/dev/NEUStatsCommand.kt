/*
 * Copyright (C) 2023 NotEnoughUpdates contributors
 *
 * This file is part of NotEnoughUpdates.
 *
 * NotEnoughUpdates is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * NotEnoughUpdates is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with NotEnoughUpdates. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.moulberry.notenoughupdates.commands.dev

import com.mojang.brigadier.context.CommandContext
import com.sun.management.OperatingSystemMXBean
import com.sun.management.UnixOperatingSystemMXBean
import io.github.moulberry.notenoughupdates.NotEnoughUpdates
import io.github.moulberry.notenoughupdates.autosubscribe.NEUAutoSubscribe
import io.github.moulberry.notenoughupdates.events.RegisterBrigadierCommandEvent
import io.github.moulberry.notenoughupdates.util.DiscordMarkdownBuilder
import io.github.moulberry.notenoughupdates.util.HastebinUploader
import io.github.moulberry.notenoughupdates.util.SBInfo
import io.github.moulberry.notenoughupdates.util.brigadier.reply
import io.github.moulberry.notenoughupdates.util.brigadier.thenExecute
import io.github.moulberry.notenoughupdates.util.brigadier.thenLiteralExecute
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.command.ICommandSender
import net.minecraft.util.EnumChatFormatting.DARK_RED
import net.minecraft.util.EnumChatFormatting.GREEN
import net.minecraftforge.common.ForgeVersion
import net.minecraftforge.fml.client.FMLClientHandler
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.lwjgl.opengl.Display
import org.lwjgl.opengl.GL11
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.lang.management.ManagementFactory
import java.util.concurrent.CompletableFuture
import javax.management.JMX
import javax.management.ObjectName

@NEUAutoSubscribe
class NEUStatsCommand {
    @SubscribeEvent
    fun onCommands(event: RegisterBrigadierCommandEvent) {
        event.command("stats", "neustats") {
            thenExecute {
                clipboardAndSendMessage(
                    DiscordMarkdownBuilder()
                        .also(::appendStats)
                        .also {
                            if (Loader.instance().activeModList.size <= 15) appendModList(it)
                        }
                        .toString()
                )
            }
            thenLiteralExecute("modlist") {
                clipboardAndSendMessage(
                    DiscordMarkdownBuilder()
                        .also(::appendModList)
                        .toString()
                )
            }
            thenLiteralExecute("full") {
                clipboardAndSendMessage(
                    DiscordMarkdownBuilder()
                        .also(::appendStats)
                        .also(::appendModList)
                        .toString()
                )
            }
            thenLiteralExecute("dump") {
                reply("${GREEN}This will upload a dump of the java classes your game has loaded how big they are and how many there are. This can take a few seconds as it is uploading to HasteBin.")
                uploadDataUsageDump().thenAccept {
                    clipboardAndSendMessage(it)
                }
            }
        }
    }
    interface DiagnosticCommandMXBean {
        fun gcClassHistogram(array: Array<String>): String
    }

    private fun uploadDataUsageDump(): CompletableFuture<String?> {
        return CompletableFuture.supplyAsync {
            try {
                val server =
                    ManagementFactory.getPlatformMBeanServer()
                val objectName =
                    ObjectName.getInstance("com.sun.management:type=DiagnosticCommand")
                val proxy = JMX.newMXBeanProxy(
                    server,
                    objectName,
                    DiagnosticCommandMXBean::class.java
                )
                HastebinUploader.upload(
                    proxy.gcClassHistogram(emptyArray()).replace("[", "[]"),
                    HastebinUploader.Mode.NORMAL
                )
            } catch (e: Exception) {
                null
            }
        }

    }


    private fun getMemorySize(): Long {
        try {
            return (ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean).totalPhysicalMemorySize
        } catch (e: java.lang.Exception) {
            try {
                return (ManagementFactory.getOperatingSystemMXBean() as UnixOperatingSystemMXBean).totalPhysicalMemorySize
            } catch (ignored: java.lang.Exception) { /*IGNORE*/
            }
        }
        return -1
    }

    val ONE_MB = 1024L * 1024L
    private fun appendStats(builder: DiscordMarkdownBuilder) {
        val maxMemory = Runtime.getRuntime().maxMemory()
        val totalMemory = Runtime.getRuntime().totalMemory()
        val freeMemory = Runtime.getRuntime().freeMemory()
        val currentMemory = totalMemory - freeMemory
        builder.category("System Stats")
        builder.append("OS", System.getProperty("os.name"))
        builder.append("CPU", OpenGlHelper.getCpu())
        builder.append(
            "Display",
            String.format("%dx%d (%s)", Display.getWidth(), Display.getHeight(), GL11.glGetString(GL11.GL_VENDOR))
        )
        builder.append("GPU", GL11.glGetString(GL11.GL_RENDERER))
        builder.append("GPU Driver", GL11.glGetString(GL11.GL_VERSION))
        if (getMemorySize() > 0)
            builder.append(
                "Maximum Memory",
                "${getMemorySize() / ONE_MB}MB"
            )
        builder.append("Shaders", ("" + OpenGlHelper.isFramebufferEnabled()).uppercase())
        builder.category("Java Stats")
        builder.append(
            "Java",
            "${System.getProperty("java.version")} ${if (Minecraft.getMinecraft().isJava64bit) 64 else 32}bit",
        )
        builder.append(
            "Memory", String.format(
                "% 2d%% %03d/%03dMB",
                currentMemory * 100L / maxMemory,
                currentMemory / ONE_MB,
                maxMemory / ONE_MB
            )
        )
        builder.append(
            "Memory Allocated",
            String.format("% 2d%% %03dMB", totalMemory * 100L / maxMemory, totalMemory / ONE_MB)
        )
        builder.category("Game Stats")
        builder.append("FPS", Minecraft.getDebugFPS().toString())
        builder.append("Loaded Mods", Loader.instance().activeModList.size)
        builder.append("Forge", ForgeVersion.getVersion())
        builder.append("Optifine", if (FMLClientHandler.instance().hasOptifine()) "TRUE" else "FALSE")
        builder.category("Neu Settings")
        builder.append("API Key", if (NotEnoughUpdates.INSTANCE.config.apiData.apiKey.isEmpty()) "FALSE" else "TRUE")
        builder.append("On SkyBlock", if (NotEnoughUpdates.INSTANCE.hasSkyblockScoreboard()) "TRUE" else "FALSE")
        builder.append(
            "Mod Version",
            Loader.instance().indexedModList[NotEnoughUpdates.MODID]!!.displayVersion
        )
        builder.append("SB Profile", SBInfo.getInstance().currentProfile)
        builder.append("Has Advanced Tab", if (SBInfo.getInstance().hasNewTab) "TRUE" else "FALSE")
        builder.category("Repo Stats")
        builder.append("Last Commit", NotEnoughUpdates.INSTANCE.manager.latestRepoCommit)
        builder.append("Loaded Items", NotEnoughUpdates.INSTANCE.manager.itemInformation.size.toString())
    }

    private fun appendModList(builder: DiscordMarkdownBuilder): DiscordMarkdownBuilder {
        builder.category("Mods Loaded")
        Loader.instance().activeModList.forEach {
            builder.append(it.name, "${it.source} (${it.displayVersion})")
        }
        return builder
    }

    fun CommandContext<ICommandSender>.clipboardAndSendMessage(data: String?) {
        if (data == null) {
            reply("${DARK_RED}Error occurred trying to perform command.")
            return
        }
        try {
            val clipboard = StringSelection(data)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(clipboard, null)
            reply("${GREEN}Dev info copied to clipboard.")
        } catch (ignored: Exception) {
            reply("${DARK_RED}Could not copy to clipboard.")
        }
    }
}