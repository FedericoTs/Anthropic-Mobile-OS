package org.agentnativeos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    @Test
    fun defaultIsTheCheapestModel() {
        assertEquals(ModelId.CHEAPEST, ModelCatalog.DEFAULT.id)
        assertEquals(ModelId.DEFAULT, ModelCatalog.DEFAULT.id)
        assertEquals("claude-haiku-4-5", ModelCatalog.DEFAULT.id.name)
    }

    @Test
    fun cheapestIsListedFirstAndOptionsAreDistinct() {
        assertSame(ModelCatalog.OPTIONS.first(), ModelCatalog.DEFAULT)
        val ids = ModelCatalog.OPTIONS.map { it.id.name }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun unknownOrNullIdFallsBackToDefault() {
        assertSame(ModelCatalog.DEFAULT, ModelCatalog.byId("claude-nonexistent"))
        assertSame(ModelCatalog.DEFAULT, ModelCatalog.byId(null as String?))
    }

    @Test
    fun knownIdResolvesToItsOption() {
        val sonnet = ModelCatalog.byId("claude-sonnet-4-6")
        assertEquals("claude-sonnet-4-6", sonnet.id.name)
        assertEquals(ModelCatalog.byId(ModelId("claude-opus-4-8")).id.name, "claude-opus-4-8")
    }

    @Test
    fun everyOptionHasUserFacingCopy() {
        for (option in ModelCatalog.OPTIONS) {
            assertTrue(option.label.isNotBlank())
            assertTrue(option.shortLabel.isNotBlank())
            assertTrue(option.tagline.isNotBlank())
            assertTrue(option.bestFor.isNotBlank())
        }
    }
}
