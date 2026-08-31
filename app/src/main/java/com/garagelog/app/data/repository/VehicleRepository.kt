package com.garagelog.app.data.repository

import com.garagelog.app.data.dao.VehicleDao
import com.garagelog.app.data.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room-backed today; the Drive sync engine reads/writes through this same interface
 * (via getAllForSync/upsert), so nothing UI-facing needed to change to add sync.
 */
interface VehicleRepository {
    fun observeAll(): Flow<List<VehicleEntity>>
    suspend fun getAll(): List<VehicleEntity>
    suspend fun getAllForSync(): List<VehicleEntity>
    suspend fun count(): Int
    suspend fun upsert(vehicle: VehicleEntity)
    suspend fun softDelete(id: String)
    suspend fun deleteAll()
    suspend fun bumpMileageIfHigher(vehicleId: String, mileage: Int, date: String)
}

class RoomVehicleRepository(private val dao: VehicleDao) : VehicleRepository {
    override fun observeAll(): Flow<List<VehicleEntity>> = dao.observeAll()
    override suspend fun getAll(): List<VehicleEntity> = dao.getAll()
    override suspend fun getAllForSync(): List<VehicleEntity> = dao.getAllForSync()
    override suspend fun count(): Int = dao.count()
    override suspend fun upsert(vehicle: VehicleEntity) = dao.upsert(vehicle)
    override suspend fun softDelete(id: String) = dao.softDeleteById(id, System.currentTimeMillis())
    override suspend fun deleteAll() = dao.deleteAll()

    override suspend fun bumpMileageIfHigher(vehicleId: String, mileage: Int, date: String) {
        val current = dao.getAll().find { it.id == vehicleId } ?: return
        if (current.miles == null || mileage > current.miles) {
            dao.bumpMileage(vehicleId, mileage, date, System.currentTimeMillis())
        }
    }
}
