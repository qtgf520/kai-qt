package com.qtkai.zhong.tools

import com.qtkai.zhong.network.tools.ParameterSchema
import com.qtkai.zhong.network.tools.Tool
import com.qtkai.zhong.network.tools.ToolInfo
import com.qtkai.zhong.network.tools.ToolSchema
import com.qtkai.zhong.skills.SkillManager
/**
 * Lets the agent inspect and manage installed skills directly from a conversation,
 * no Settings screen required - install a skill from GitHub, list what's installed,
 * or remove one. All three stay safe for agentic loops: deterministic, idempotent,
 * and they never touch the sandbox shell.
 */
object SkillManagementTools {

    // Injected lazily and defensively: getPlatformToolDefinitions() can be called before
    // Koin is ready (screenshot/unit-test paths), and touching an `inject` property at that
    // point would crash tool-list construction. The getters below return null instead, and
    // execute() reports the failure to the model instead of throwing.
    private val skillManager: SkillManager?
        get() = try {
            org.koin.java.KoinJavaComponent.get(SkillManager::class.java)
        } catch (_: Throwable) {
            null
        }

    val listSkillsToolInfo = ToolInfo(
        id = "list_skills",
        name = "List Skills",
        description = "List every installed skill and its one-line description.",
        isEnabled = true,
        userToggleable = false,
    )
    val installSkillToolInfo = ToolInfo(
        id = "install_skill",
        name = "Install Skill",
        description = "Install a skill from a GitHub repo containing a SKILL.md.",
        isEnabled = true,
        userToggleable = false,
    )
    val browseSkillsToolInfo = ToolInfo(
        id = "browse_skills",
        name = "Browse Skills",
        description = "Search the curated skill marketplaces for installable skills.",
        isEnabled = true,
        userToggleable = false,
    )
    val uninstallSkillToolInfo = ToolInfo(
        id = "uninstall_skill",
        name = "Uninstall Skill",
        description = "Uninstall a skill by its id. Built-in skills cannot be removed.",
        isEnabled = true,
        userToggleable = false,
    )

    val listSkillsTool = object : Tool {
        override val schema = ToolSchema(
            name = "list_skills",
            description = "List every installed skill and its one-line description. Use this first to see what abilities are already available before deciding whether you need to install anything. Returns id, display name, description, and whether the skill is built-in.",
            parameters = emptyMap(),
        )
        override suspend fun execute(args: Map<String, Any>): Any {
            val mgr = skillManager
                ?: return mapOf("success" to false, "error" to "Skill manager is not ready yet — try again shortly")
            val skills = mgr.getInstalled()
            if (skills.isEmpty()) return mapOf("success" to true, "skills" to emptyList<Any>(), "count" to 0)
            val list = skills.map {
                mapOf(
                    "id" to it.id,
                    "name" to it.displayName,
                    "description" to it.description,
                    "built_in" to it.isBuiltIn,
                )
            }
            return mapOf("success" to true, "skills" to list, "count" to list.size)
        }
    }

    val uninstallSkillTool = object : Tool {
        override val schema = ToolSchema(
            name = "uninstall_skill",
            description = "Uninstall a skill by its id (the id you saw in list_skills). The skill folder is removed from the sandbox and it stops being advertised. Built-in skills cannot be uninstalled. Returns whether removal succeeded.",
            parameters = mapOf(
                "id" to ParameterSchema("string", "The skill id to uninstall", true),
            ),
        )
        override suspend fun execute(args: Map<String, Any>): Any {
            val id = args["id"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return mapOf("success" to false, "error" to "Missing id")
            val mgr = skillManager
                ?: return mapOf("success" to false, "error" to "Skill manager is not ready yet — try again shortly")
            val skill = mgr.getSkill(id)
            if (skill == null) return mapOf("success" to false, "error" to "No installed skill with id '$id'")
            if (skill.isBuiltIn) return mapOf("success" to false, "error" to "Skill '$id' is built-in and cannot be uninstalled")
            mgr.uninstall(id)
            return mapOf("success" to true, "uninstalled" to id)
        }
    }

    val installSkillTool = object : Tool {
        override val schema = ToolSchema(
            name = "install_skill",
            description = "Install a skill from a GitHub repo. Owner/repo/ref/path point at a folder containing a SKILL.md (e.g. owner=openai, repo=my-skills, path=web-research). Returns the installed skill manifest. Built-in skills with the same id are shadowed by a sandbox install.",
            parameters = mapOf(
                "owner" to ParameterSchema("string", "GitHub owner or org", true),
                "repo" to ParameterSchema("string", "GitHub repository name", true),
                "path" to ParameterSchema("string", "Folder inside the repo containing SKILL.md", true),
                "ref" to ParameterSchema("string", "Branch/tag/commit ref, defaults to main", false),
            ),
        )
        override suspend fun execute(args: Map<String, Any>): Any {
            val owner = args["owner"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return mapOf("success" to false, "error" to "Missing owner")
            val repo = args["repo"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return mapOf("success" to false, "error" to "Missing repo")
            val path = args["path"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return mapOf("success" to false, "error" to "Missing path")
            val ref = args["ref"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "main"
            val mgr = skillManager
                ?: return mapOf("success" to false, "error" to "Skill manager is not ready yet — try again shortly")
            return mgr.installFromGitHub(owner = owner, repo = repo, ref = ref, path = path)
                .fold(
                    onSuccess = { m -> mapOf("success" to true, "id" to m.id, "name" to m.displayName, "description" to m.description) },
                    onFailure = { e -> mapOf("success" to false, "error" to (e.message ?: "install failed")) },
                )
        }
    }

    val browseSkillsTool = object : Tool {
        override val schema = ToolSchema(
            name = "browse_skills",
            description = "Search the curated skill marketplaces for installable skills. Returns a list of available skills with their id and description plus the repo coordinates needed to install. Use this to discover what the user can add, then call install_skill with the coordinates you picked — or tell the user the option. Infers the best match when a query is supplied.",
            parameters = mapOf(
                "query" to ParameterSchema("string", "Optional keyword to filter the listings by (searches id and description)", false),
            ),
        )
        override suspend fun execute(args: Map<String, Any>): Any {
            val query = args["query"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()
            val mgr = skillManager
                ?: return mapOf("success" to false, "error" to "Skill manager is not ready yet — try again shortly")
            return mgr.browseMarketplaces().fold(
                onSuccess = { entries ->
                    val filtered = query?.let { q ->
                        entries.filter {
                            it.id.contains(q, ignoreCase = true) ||
                                it.description.contains(q, ignoreCase = true) ||
                                it.sourceName.contains(q, ignoreCase = true)
                        }
                    } ?: entries
                    if (filtered.isEmpty()) {
                        mapOf("success" to true, "count" to 0, "skills" to emptyList<Any>(), "query" to (query ?: ""))
                    } else {
                        val list = filtered.map {
                            mapOf(
                                "id" to it.id,
                                "description" to it.description,
                                "source" to it.sourceName,
                                "owner" to it.owner,
                                "repo" to it.repo,
                                "path" to it.skillPath,
                                "requires_sandbox" to it.requiresSandbox,
                            )
                        }
                        mapOf("success" to true, "count" to list.size, "skills" to list, "query" to (query ?: ""))
                    }
                },
                onFailure = { e -> mapOf("success" to false, "error" to (e.message ?: "browse failed")) },
            )
        }
    }
}