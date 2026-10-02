/**
 * Events core posts on {@code NeoForge.EVENT_BUS}, on the server only, after the change is done. None can be
 * cancelled. Some fire often (redstone power is a data change), so listeners must be cheap.
 */
package dev.thefern2.tinytunnels.api.event;
