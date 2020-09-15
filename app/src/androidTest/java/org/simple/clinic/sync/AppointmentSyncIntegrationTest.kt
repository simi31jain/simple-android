package org.simple.clinic.sync

import com.f2prateek.rx.preferences2.Preference
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.simple.clinic.AppDatabase
import org.simple.clinic.TestClinicApp
import org.simple.clinic.TestData
import org.simple.clinic.overdue.Appointment
import org.simple.clinic.overdue.AppointmentRepository
import org.simple.clinic.overdue.AppointmentSync
import org.simple.clinic.overdue.AppointmentSyncApi
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.rules.ServerAuthenticationRule
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.Optional
import org.simple.clinic.util.Rules
import org.simple.clinic.util.unsafeLazy
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

class AppointmentSyncIntegrationTest {

  @Inject
  lateinit var appDatabase: AppDatabase

  @Inject
  lateinit var repository: AppointmentRepository

  @Inject
  @Named("last_appointment_pull_token")
  lateinit var lastPullToken: Preference<Optional<String>>

  @Inject
  lateinit var syncApi: AppointmentSyncApi

  @Inject
  lateinit var userSession: UserSession

  @get:Rule
  val ruleChain: RuleChain = Rules
      .global()
      .around(ServerAuthenticationRule())

  private lateinit var sync: AppointmentSync

  private val batchSize = 3
  private val config = SyncConfig(
      syncInterval = SyncInterval.FREQUENT,
      batchSize = batchSize,
      syncGroup = SyncGroup.FREQUENT
  )

  private val currentFacilityUuid: UUID by unsafeLazy { userSession.loggedInUserImmediate()!!.currentFacilityUuid }

  @Before
  fun setUp() {
    TestClinicApp.appComponent().inject(this)

    resetLocalData()

    sync = AppointmentSync(
        syncCoordinator = SyncCoordinator(),
        repository = repository,
        api = syncApi,
        lastPullToken = lastPullToken,
        config = config
    )
  }

  @After
  fun tearDown() {
    resetLocalData()
  }

  private fun resetLocalData() {
    clearAppointmentData()
    lastPullToken.delete()
  }

  private fun clearAppointmentData() {
    appDatabase.appointmentDao().clear()
  }

  @Test
  fun syncing_records_should_work_as_expected() {
    // given
    val totalNumberOfRecords = batchSize * 2 + 1
    val records = (1..totalNumberOfRecords).map {
      TestData.appointment(
          syncStatus = SyncStatus.PENDING,
          facilityUuid = currentFacilityUuid
      )
    }
    assertThat(records).containsNoDuplicates()

    repository.save(records).blockingAwait()
    assertThat(repository.pendingSyncRecordCount().blockingFirst()).isEqualTo(totalNumberOfRecords)

    // when
    sync.push()
    clearAppointmentData()
    sync.pull()

    // then
    val expectedPulledRecords = records.map { it.syncCompleted() }
    val pulledRecords = repository.recordsWithSyncStatus(SyncStatus.DONE)

    assertThat(pulledRecords).containsAtLeastElementsIn(expectedPulledRecords)
  }

  @Test
  fun sync_pending_records_should_not_be_overwritten_by_server_records() {
    // given
    val records = (1..batchSize).map {
      TestData.appointment(
          syncStatus = SyncStatus.PENDING,
          facilityUuid = currentFacilityUuid
      )
    }
    assertThat(records).containsNoDuplicates()

    repository.save(records).blockingAwait()
    sync.push()
    assertThat(repository.pendingSyncRecordCount().blockingFirst()).isEqualTo(0)

    val modifiedRecord = records[1].agreedToVisit()
    repository.save(listOf(modifiedRecord)).blockingAwait()
    assertThat(repository.pendingSyncRecordCount().blockingFirst()).isEqualTo(1)

    // when
    sync.pull()

    // then
    val expectedSavedRecords = records
        .map { it.syncCompleted() }
        .filterNot { it.patientUuid == modifiedRecord.patientUuid }

    val savedRecords = repository.recordsWithSyncStatus(SyncStatus.DONE)
    val pendingSyncRecords = repository.recordsWithSyncStatus(SyncStatus.PENDING)

    assertThat(savedRecords).containsAtLeastElementsIn(expectedSavedRecords)
    assertThat(pendingSyncRecords).containsExactly(modifiedRecord)
  }

  private fun Appointment.syncCompleted(): Appointment = copy(syncStatus = SyncStatus.DONE)

  private fun Appointment.agreedToVisit(): Appointment {
    return copy(
        agreedToVisit = true,
        syncStatus = SyncStatus.PENDING,
        updatedAt = updatedAt.plusMillis(1)
    )
  }
}