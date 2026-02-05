package com.example.todostart

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object TaskStorage {

    private const val FILE_NAME = "tasks.json"

    fun load(context: Context): List<Task> {
        return try {
            val json = context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
            fromJson(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, tasks: List<Task>) {
        try {
            val json = toJson(tasks)
            context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            // celowo bez crashowania — w razie problemu po prostu nie zapisze
        }
    }

    private fun toJson(tasks: List<Task>): String {
        val arr = JSONArray()
        for (t in tasks) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("title", t.title)
            obj.put("description", t.description)
            obj.put("isDone", t.isDone)
            obj.put("priority", t.priority.name)
            if (t.dueAtMillis == null) obj.put("dueAtMillis", JSONObject.NULL)
            else obj.put("dueAtMillis", t.dueAtMillis)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun fromJson(json: String): List<Task> {
        val arr = JSONArray(json)
        val list = mutableListOf<Task>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)

            val id = obj.getLong("id")
            val title = obj.getString("title")
            val description = obj.optString("description", "")
            val isDone = obj.optBoolean("isDone", false)
            val priority = Priority.valueOf(obj.optString("priority", Priority.MEDIUM.name))

            val dueAny = obj.opt("dueAtMillis")
            val dueAtMillis: Long? =
                if (dueAny == null || dueAny == JSONObject.NULL) null
                else (dueAny as Number).toLong()

            list.add(
                Task(
                    id = id,
                    title = title,
                    description = description,
                    isDone = isDone,
                    priority = priority,
                    dueAtMillis = dueAtMillis
                )
            )
        }
        // nowsze na górze
        return list.sortedByDescending { it.id }
    }
}
