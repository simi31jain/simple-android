package org.simple.clinic.home.overdue.search

import androidx.paging.PagingData
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Observable
import org.junit.After
import org.junit.Test
import org.simple.clinic.home.overdue.OverdueAppointment
import org.simple.clinic.home.overdue.search.OverdueButtonType.DOWNLOAD
import org.simple.clinic.mobius.EffectHandlerTestCase
import org.simple.clinic.overdue.AppointmentRepository
import org.simple.clinic.overdue.OverdueAppointmentSelector
import org.simple.clinic.overdue.download.OverdueDownloadScheduler
import org.simple.clinic.overdue.download.OverdueListFileFormat.CSV
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.util.PagerFactory
import org.simple.clinic.util.PagingSourceFactory
import org.simple.clinic.util.scheduler.TestSchedulersProvider
import org.simple.sharedTestCode.TestData
import java.time.LocalDate
import java.util.UUID

class OverdueSearchEffectHandlerTest {

  private val pagingLoadSize = 15

  private val overdueSearchConfig = OverdueSearchConfig(searchHistoryLimit = 5, pagingLoadSize = pagingLoadSize)
  private val uiActions = mock<OverdueSearchUiActions>()
  private val viewEffectHandler = OverdueSearchViewEffectHandler(uiActions)
  private val appointmentRepository = mock<AppointmentRepository>()
  private val pagerFactory = mock<PagerFactory>()
  private val currentFacility = TestData.facility(uuid = UUID.fromString("94db5d90-d483-4755-892a-97fde5a870fe"))
  private val overdueAppointmentSelector = mock<OverdueAppointmentSelector>()
  private val overdueDownloadScheduler = mock<OverdueDownloadScheduler>()
  private val patientRepository = mock<PatientRepository>()
  private val effectHandler = OverdueSearchEffectHandler(
      schedulersProvider = TestSchedulersProvider.trampoline(),
      appointmentRepository = appointmentRepository,
      pagerFactory = pagerFactory,
      overdueSearchConfig = overdueSearchConfig,
      currentFacility = { currentFacility },
      overdueAppointmentSelector = overdueAppointmentSelector,
      overdueDownloadScheduler = overdueDownloadScheduler,
      patientRepository = patientRepository,
      viewEffectsConsumer = viewEffectHandler::handle
  ).build()
  private val effectHandlerTestCase = EffectHandlerTestCase(effectHandler)

  @After
  fun tearDown() {
    effectHandlerTestCase.dispose()
  }

  @Test
  fun `when open patient summary view effect is received, then open patient summary screen`() {
    // given
    val patientUuid = UUID.fromString("fc831110-5da8-4bea-9036-5b5d6334dc1a")

    // when
    effectHandlerTestCase.dispatch(OpenPatientSummary(patientUuid))

    // then
    effectHandlerTestCase.assertNoOutgoingEvents()

    verify(uiActions).openPatientSummaryScreen(patientUuid)
    verifyNoMoreInteractions(uiActions)
  }

  @Test
  fun `when call overdue patient view effect is received, then open contact patient sheet`() {
    // given
    val patientUuid = UUID.fromString("fc831110-5da8-4bea-9036-5b5d6334dc1a")

    // when
    effectHandlerTestCase.dispatch(OpenContactPatientSheet(patientUuid))

    // then
    effectHandlerTestCase.assertNoOutgoingEvents()

    verify(uiActions).openContactPatientSheet(patientUuid)
    verifyNoMoreInteractions(uiActions)
  }

  @Test
  fun `when search overdue patient effect is received, then search overdue patient`() {
    // given
    val searchInputs = listOf("Ani")
    val overdueAppointment = listOf(TestData.overdueAppointment(
        facilityUuid = currentFacility.uuid,
        name = "Anish Acharya",
        patientUuid = UUID.fromString("37259e96-e757-4608-aeae-f1a20b088f09")
    ), TestData.overdueAppointment(
        facilityUuid = currentFacility.uuid,
        name = "Anirban Dar",
        patientUuid = UUID.fromString("53659148-a157-4aa4-92fb-c0a7991ae872")
    ))

    val expectedPagingData = PagingData.from(overdueAppointment)

    whenever(pagerFactory.createPager(
        sourceFactory = any<PagingSourceFactory<Int, OverdueAppointment>>(),
        pageSize = eq(pagingLoadSize),
        enablePlaceholders = eq(false),
        initialKey = eq(null)
    )) doReturn Observable.just(expectedPagingData)

    // when
    effectHandlerTestCase.dispatch(SearchOverduePatients(searchInputs, LocalDate.of(2022, 3, 22)))

    // then
    effectHandlerTestCase.assertOutgoingEvents(OverdueSearchResultsLoaded(expectedPagingData))
    verifyZeroInteractions(uiActions)
  }

  @Test
  fun `when toggle overdue appointment selection effect is received, then toggle the overdue appointment selection`() {
    // given
    val appointmentId = UUID.fromString("97667670-26b9-42ed-ab09-f7174c5ade7f")

    // when
    effectHandlerTestCase.dispatch(ToggleOverdueAppointmentSelection(appointmentId))

    // then
    effectHandlerTestCase.assertNoOutgoingEvents()

    verify(overdueAppointmentSelector).toggleSelection(appointmentId)
    verifyNoMoreInteractions(overdueAppointmentSelector)

    verifyZeroInteractions(uiActions)
  }

  @Test
  fun `when load selected overdue appointments effect is received, then load the selected overdue appointments`() {
    // given
    val selectedAppointmentIds = setOf(
        UUID.fromString("b224924c-81b0-42ba-b876-cb6d8253e47c"),
        UUID.fromString("3af05d37-8c6c-4a03-9b3c-81f3d5152bd0")
    )

    whenever(overdueAppointmentSelector.selectedAppointmentIdsStream) doReturn Observable.just(selectedAppointmentIds)

    // when
    effectHandlerTestCase.dispatch(LoadSelectedOverdueAppointmentIds)

    // then
    effectHandlerTestCase.assertOutgoingEvents(SelectedOverdueAppointmentsLoaded(selectedAppointmentIds))

    verifyZeroInteractions(uiActions)
  }

  @Test
  fun `when clear selected overdue appointments effect is received, then clear the selected overdue appointments`() {
    // when
    effectHandlerTestCase.dispatch(ClearSelectedOverdueAppointments)

    // then
    effectHandlerTestCase.assertNoOutgoingEvents()

    verify(overdueAppointmentSelector).clearSelection()
    verifyNoMoreInteractions(overdueAppointmentSelector)

    verifyZeroInteractions(uiActions)
  }

  @Test
  fun `when replace selected overdue appointment ids effect is received, then replace the selected ids`() {
    // given
    val appointmentIds = setOf(
        UUID.fromString("e9fd3636-34d9-4959-9c83-065ef414b836"),
        UUID.fromString("5e2a88d8-1556-44dd-940d-17736a311c6a")
    )

    // when
    effectHandlerTestCase.dispatch(ReplaceSelectedAppointmentIds(appointmentIds, DOWNLOAD))

    // then
    verifyZeroInteractions(uiActions)
    verify(overdueAppointmentSelector).replaceSelectedIds(appointmentIds)
    verifyNoMoreInteractions(overdueAppointmentSelector)
    effectHandlerTestCase.assertOutgoingEvents(SelectedAppointmentIdsReplaced(DOWNLOAD))
  }

  @Test
  fun `when schedule download effect is received, then schedule download`() {
    // when
    effectHandlerTestCase.dispatch(ScheduleDownload)

    // then
    verify(overdueDownloadScheduler).schedule(CSV)
    verifyNoMoreInteractions(overdueDownloadScheduler)
    effectHandlerTestCase.assertNoOutgoingEvents()
    verifyZeroInteractions(uiActions)
  }

  @Test
  fun `when open select download format dialog effect is received, then open dialog`() {
    // when
    effectHandlerTestCase.dispatch(OpenSelectDownloadFormatDialog)

    // then
    verify(uiActions).openSelectDownloadFormatDialog()
    verifyNoMoreInteractions(uiActions)
    effectHandlerTestCase.assertNoOutgoingEvents()
  }

  @Test
  fun `when open select share format dialog effect is received, then open dialog`() {
    // when
    effectHandlerTestCase.dispatch(OpenSelectShareFormatDialog)

    // then
    verify(uiActions).openSelectShareFormatDialog()
    verifyNoMoreInteractions(uiActions)
    effectHandlerTestCase.assertNoOutgoingEvents()
  }

  @Test
  fun `when open share in progress dialog effect is received, then open dialog`() {
    // when
    effectHandlerTestCase.dispatch(OpenShareInProgressDialog)

    // then
    verify(uiActions).openShareInProgressDialog()
    verifyNoMoreInteractions(uiActions)
    effectHandlerTestCase.assertNoOutgoingEvents()
  }

  @Test
  fun `when show no internet connection effect is received, then show dialog`() {
    // when
    effectHandlerTestCase.dispatch(ShowNoInternetConnectionDialog)

    // then
    verify(uiActions).showNoInternetConnectionDialog()
    verifyNoMoreInteractions(uiActions)
    effectHandlerTestCase.assertNoOutgoingEvents()
  }

  @Test
  fun `when select all appointment ids effect is received, then add all selected ids`() {
    // given
    val allAppointmentIds = setOf(
        UUID.fromString("9737ad9e-70f6-454e-9226-6eaa01c62645"))

    // when
    effectHandlerTestCase.dispatch(SelectAllAppointmentIds(allAppointmentIds))

    // then
    verifyZeroInteractions(uiActions)
    verify(overdueAppointmentSelector).addSelectedIds(allAppointmentIds)
    verifyNoMoreInteractions(overdueAppointmentSelector)
    effectHandlerTestCase.assertNoOutgoingEvents()
  }

  @Test
  fun `when load search results appointment ids effect is received, then load the search results appointment ids`() {
    // given
    val searchInputs = listOf("Ani")
    val since = LocalDate.parse("2018-01-01")
    val appointmentUuid = UUID.fromString("46797d57-d6a9-4aee-9df5-355ced4bb9a4")
    val overdueAppointments = listOf(
        TestData.overdueAppointment(appointmentUuid = appointmentUuid)
    )

    whenever(appointmentRepository.searchOverduePatientsImmediate(searchInputs, since, currentFacility.uuid)) doReturn overdueAppointments

    // when
    effectHandlerTestCase.dispatch(LoadSearchResultsAppointmentIds(
        buttonType = DOWNLOAD,
        searchInputs = searchInputs,
        since = since
    ))

    // then
    verifyZeroInteractions(uiActions)
    effectHandlerTestCase.assertOutgoingEvents(SearchResultsAppointmentIdsLoaded(DOWNLOAD, setOf(appointmentUuid)))
  }

  @Test
  fun `when load village and patient names effect is received, then load village and patient names`() {
    //given
    val villagesAndPatientNames = listOf("Anand", "Anup", "Asia", "Earth")

    whenever(patientRepository.villageAndPatientNamesInFacility(currentFacility.uuid)) doReturn villagesAndPatientNames

    // when
    effectHandlerTestCase.dispatch(LoadVillageAndPatientNames)

    // then
    effectHandlerTestCase.assertOutgoingEvents(VillagesAndPatientNamesLoaded(villagesAndPatientNames))
    verifyZeroInteractions(uiActions)
  }

  @Test
  fun `when set overdue search paging data effect is received, then set overdue search paging data`() {
    // give
    val selectedOverdueAppointments = setOf(
        UUID.fromString("931d4406-78d1-4cdf-aad5-d8f4a9e18f38")
    )
    val searchResults = PagingData.from(listOf(
        TestData.overdueAppointment(
            patientUuid = UUID.fromString("92fc941d-a6f5-427f-a9f3-0e703373d03e"),
            appointmentUuid = UUID.fromString("931d4406-78d1-4cdf-aad5-d8f4a9e18f38"),
            name = "Anish Acharya"
        )
    ))

    // when
    effectHandlerTestCase.dispatch(SetOverdueSearchPagingData(
        overdueSearchResults = searchResults,
        selectedOverdueAppointments = selectedOverdueAppointments
    ))

    verify(uiActions).setOverdueSearchResultsPagingData(
        overdueSearchResults = searchResults,
        selectedOverdueAppointments = selectedOverdueAppointments
    )
    verifyNoMoreInteractions(uiActions)
  }

  @Test
  fun `when search overdue patients effect is received, then search for overdue patients`() {
    // given
    val searchInputs = listOf(
        "Babri",
        "Narwar",
        "Anandh"
    )
    val since = LocalDate.parse("2018-01-01")

    val searchResults = PagingData.from(listOf(
        TestData.overdueAppointment(appointmentUuid = UUID.fromString("0da6d783-0ba8-4c06-8a29-815d013bda6d"))
    ))

    whenever(pagerFactory.createPager(
        sourceFactory = any<PagingSourceFactory<Int, OverdueAppointment>>(),
        pageSize = eq(pagingLoadSize),
        enablePlaceholders = eq(false),
        initialKey = eq(null)
    )) doReturn Observable.just(searchResults)

    // when
    effectHandlerTestCase.dispatch(SearchOverduePatients(searchInputs, since))

    // then
    verifyZeroInteractions(uiActions)

    effectHandlerTestCase.assertOutgoingEvents(OverdueSearchResultsLoaded(searchResults))
  }
}
