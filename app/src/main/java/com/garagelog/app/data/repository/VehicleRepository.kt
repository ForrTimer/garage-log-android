package com.garagelog.app.data.repository

import com.garagelog.app.data.dao.VehicleDao
import com.garagelog.app.data.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Local-only today (Room-backed). Kept as an interface so a sync-aware
 * implementation can be swapped in later without touching ViewModels/UI.
 */
interface VehicleRepository {
    fun observeAll(): Flow<List<VehicleEntity>>
    suspend fun getAll(): List<VehicleEntity>
    suspend fun count(): Int
    suspend fun upsert(vehicle: VehicleEntity)
    suspend fun delete(id: String)
    suspend fun deleteAll()
    suspend fun bumpMileageIfHigher(vehicleId: String, mileage: Int, date: String)
}

class RoomVehicleRepository(private val dao: VehicleDao) : VehicleRepository {
    override fun observeAll(): Flow<List<VehicleEntity>> = dao.observeAll()
    override suspend fun getAll(): List<VehicleEntity> = dao.getAll()
    override suspend fun count(): Int = dao.count()
    override suspend fun upsert(vehicle: VehicleEntity) = dao.upsert(vehicle)
    override suspend fun delete(id: String) = dao.deleteById(id)
    override suspend fun deleteAll() = dao.deleteAll()

    override suspend fun bumpMileageIfHigher(vehicleId: String, mileage: Int, date: String) {
        val current = dao.getAll().find { it.id == vehicleId } ?: return
        if (current.miles == null || mileage > current.miles) {
            dao.bumpMileage(vehicleId, mileage, date)
        }
    }
}
