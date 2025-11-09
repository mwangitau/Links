package com.githow.links.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.githow.links.data.entity.Person

@Dao
interface PersonDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: Person): Long

    @Update
    suspend fun updatePerson(person: Person)

    @Delete
    suspend fun deletePerson(person: Person)

    // Get all active persons
    @Query("SELECT * FROM persons WHERE is_active = 1 ORDER BY display_order ASC")
    fun getAllActivePersons(): LiveData<List<Person>>

    // Get all persons (including inactive)
    @Query("SELECT * FROM persons ORDER BY display_order ASC")
    fun getAllPersons(): LiveData<List<Person>>

    // Get person by ID
    @Query("SELECT * FROM persons WHERE person_id = :id")
    suspend fun getPersonById(id: Long): Person?

    // Get person by short name
    @Query("SELECT * FROM persons WHERE short_name = :shortName LIMIT 1")
    suspend fun getPersonByShortName(shortName: String): Person?

    // Deactivate person (soft delete)
    @Query("UPDATE persons SET is_active = 0, updated_at = :timestamp WHERE person_id = :id")
    suspend fun deactivatePerson(id: Long, timestamp: Long = System.currentTimeMillis())

    // Reactivate person
    @Query("UPDATE persons SET is_active = 1, updated_at = :timestamp WHERE person_id = :id")
    suspend fun reactivatePerson(id: Long, timestamp: Long = System.currentTimeMillis())

    // Get count of persons
    @Query("SELECT COUNT(*) FROM persons WHERE is_active = 1")
    suspend fun getActivePersonCount(): Int
}