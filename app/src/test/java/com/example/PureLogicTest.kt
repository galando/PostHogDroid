package com.example

import com.example.data.database.AlertEntity
import com.example.data.repository.RepoSeries
import com.example.data.repository.evaluateCondition
import com.example.data.repository.extractMetricValue
import com.example.data.repository.parseRepositoryDataJson
import org.junit.Assert.*
import org.junit.Test

class ParseRepositoryDataJsonTest {

    @Test
    fun `empty input returns empty list`() {
        val result = parseRepositoryDataJson("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `blank input returns empty list`() {
        val result = parseRepositoryDataJson("   ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single series comma-separated values`() {
        val result = parseRepositoryDataJson("10.0,20.0,30.0")
        assertEquals(1, result.size)
        assertEquals("Metric", result[0].name)
        assertEquals(listOf(10.0, 20.0, 30.0), result[0].data)
    }

    @Test
    fun `multi-series with pipe and colon separators`() {
        val input = "SeriesA:1.0,2.0,3.0|SeriesB:4.0,5.0,6.0"
        val result = parseRepositoryDataJson(input)
        assertEquals(2, result.size)
        assertEquals("SeriesA", result[0].name)
        assertEquals(listOf(1.0, 2.0, 3.0), result[0].data)
        assertEquals("SeriesB", result[1].name)
        assertEquals(listOf(4.0, 5.0, 6.0), result[1].data)
    }

    @Test
    fun `pipe separated without colon uses Metric as name`() {
        val input = "1.0,2.0|3.0,4.0"
        val result = parseRepositoryDataJson(input)
        assertEquals(2, result.size)
        assertEquals("Metric", result[0].name)
        assertEquals("Metric", result[1].name)
    }

    @Test
    fun `malformed values are filtered out`() {
        val result = parseRepositoryDataJson("abc,def")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `mixed valid and invalid values`() {
        val result = parseRepositoryDataJson("10.0,invalid,30.0")
        assertEquals(1, result.size)
        assertEquals(listOf(10.0, 30.0), result[0].data)
    }

    @Test
    fun `empty series after parsing filtered out`() {
        val input = "SeriesA:|SeriesB:1.0,2.0"
        val result = parseRepositoryDataJson(input)
        assertEquals(1, result.size)
        assertEquals("SeriesB", result[0].name)
    }

    @Test
    fun `single value series`() {
        val result = parseRepositoryDataJson("42.0")
        assertEquals(1, result.size)
        assertEquals(listOf(42.0), result[0].data)
    }
}

class MapRemoteInsightToEntityTest {

    @Test
    fun `line graph with single series in result list`() {
        val parsed = parseRepositoryDataJson("Visitors:10.0,20.0,30.0")
        assertEquals(1, parsed.size)
        assertEquals(listOf(10.0, 20.0, 30.0), parsed[0].data)
    }

    @Test
    fun `pie chart data parsed correctly`() {
        val parsed = parseRepositoryDataJson("Chrome:50.0|Firefox:30.0|Safari:20.0")
        assertEquals(3, parsed.size)
        assertEquals(50.0, parsed[0].data.first(), 0.01)
        assertEquals(30.0, parsed[1].data.first(), 0.01)
        assertEquals(20.0, parsed[2].data.first(), 0.01)
    }

    @Test
    fun `empty result produces empty series`() {
        val parsed = parseRepositoryDataJson("")
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `nested results with pipe separator`() {
        val input = "Desktop:100.0,200.0,300.0|Mobile:50.0,75.0,100.0"
        val parsed = parseRepositoryDataJson(input)
        assertEquals(2, parsed.size)
        assertEquals("Desktop", parsed[0].name)
        assertEquals("Mobile", parsed[1].name)
        assertEquals(3, parsed[0].data.size)
        assertEquals(3, parsed[1].data.size)
    }
}

class EvaluateAlertsLogicTest {

    // Tests call the actual evaluateCondition and extractMetricValue functions
    // used by PostHogRepository.evaluateAlertsDirect()

    @Test
    fun `alert triggers when value is above threshold`() {
        assertTrue(evaluateCondition("ABOVE", 600.0, 500.0))
    }

    @Test
    fun `alert does not trigger when value is below threshold`() {
        assertFalse(evaluateCondition("ABOVE", 400.0, 500.0))
    }

    @Test
    fun `below condition triggers when value is below threshold`() {
        assertTrue(evaluateCondition("BELOW", 98.0, 99.5))
    }

    @Test
    fun `below condition does not trigger when value is above threshold`() {
        assertFalse(evaluateCondition("BELOW", 99.9, 99.5))
    }

    @Test
    fun `muted alert still evaluates condition but should not notify`() {
        val alert = AlertEntity(
            id = 3, name = "Muted Alert", insightId = 102, insightName = "Errors",
            metric = "Value", condition = "ABOVE", threshold = 100.0,
            currentValue = 0.0, isActive = true, isTriggered = false, status = "OK",
            isMuted = true
        )
        assertTrue(evaluateCondition(alert.condition, 150.0, alert.threshold))
        assertTrue(alert.isMuted)
    }

    @Test
    fun `inactive alert is not evaluated`() {
        val alert = AlertEntity(
            id = 4, name = "Inactive Alert", insightId = 103, insightName = "Inactive",
            metric = "Value", condition = "ABOVE", threshold = 100.0,
            currentValue = 0.0, isActive = false, isTriggered = false, status = "OK"
        )
        assertFalse(alert.isActive)
    }

    @Test
    fun `metric value extraction from single series`() {
        val series = listOf(RepoSeries("Metric", listOf(10.0, 20.0, 30.0)))
        assertEquals(30.0, extractMetricValue(series), 0.01)
    }

    @Test
    fun `metric value extraction from multi series sums all`() {
        val series = listOf(
            RepoSeries("A", listOf(10.0, 20.0)),
            RepoSeries("B", listOf(5.0, 15.0))
        )
        assertEquals(35.0, extractMetricValue(series), 0.01)
    }

    @Test
    fun `metric value extraction from empty series returns zero`() {
        assertEquals(0.0, extractMetricValue(emptyList()), 0.01)
    }

    @Test
    fun `exact threshold value does not trigger above`() {
        assertFalse(evaluateCondition("ABOVE", 100.0, 100.0))
    }
}
