package com.github.mikephil.charting.renderer

import android.graphics.Canvas
import android.graphics.RectF

import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.buffer.BarBuffer
import com.github.mikephil.charting.buffer.HorizontalBarBuffer
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.dataprovider.BarDataProvider
import com.github.mikephil.charting.interfaces.dataprovider.ChartInterface
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.utils.MPPointF
import com.github.mikephil.charting.utils.Transformer
import com.github.mikephil.charting.utils.Utils
import com.github.mikephil.charting.utils.ViewPortHandler

class HorizontalBarChartRender(
        chart: BarDataProvider?,
        animator: ChartAnimator?,
        viewPortHandler: ViewPortHandler?
) : BarChartRenderer(chart, animator, viewPortHandler) {

    private val mBarShadowRectBuffer = RectF()
    private val valueOffsetPlus = Utils.convertDpToPixel(5f)

    override fun initBuffers() {
        val barData = mChart.barData

        mBarBuffers = Array(barData.dataSetCount) { i ->
            val set = barData.getDataSetByIndex(i)
            HorizontalBarBuffer(
                    set.entryCount * 4 * (if (set.isStacked) set.stackSize else 1),
                    barData.dataSetCount,
                    set.isStacked
            )
        }
    }

    override fun drawDataSet(c: Canvas, dataSet: IBarDataSet, index: Int) {
        val trans = mChart.getTransformer(dataSet.axisDependency)

        mBarBorderPaint.color = dataSet.barBorderColor
        mBarBorderPaint.strokeWidth = Utils.convertDpToPixel(dataSet.barBorderWidth)

        val drawBorder = dataSet.barBorderWidth > 0f

        val phaseX = mAnimator.phaseX
        val phaseY = mAnimator.phaseY

        // draw the bar shadow before the values
        if (mChart.isDrawBarShadowEnabled) {
            mShadowPaint.color = dataSet.barShadowColor

            val barData = mChart.barData

            val barWidth = barData.barWidth
            val barWidthHalf = barWidth / 2.0f
            var x: Float

            var i = 0
            val count = Math.min(Math.ceil((dataSet.entryCount.toFloat() * phaseX).toDouble()).toInt(), dataSet.entryCount)
            while (i < count) {

                val e = dataSet.getEntryForIndex(i)

                x = e.x

                mBarShadowRectBuffer.top = x - barWidthHalf
                mBarShadowRectBuffer.bottom = x + barWidthHalf

                trans.rectValueToPixel(mBarShadowRectBuffer)

                if (!mViewPortHandler.isInBoundsTop(mBarShadowRectBuffer.bottom)) {
                    i++
                    continue
                }

                if (!mViewPortHandler.isInBoundsBottom(mBarShadowRectBuffer.top))
                    break

                mBarShadowRectBuffer.left = mViewPortHandler.contentLeft()
                mBarShadowRectBuffer.right = mViewPortHandler.contentRight()

                c.drawRect(mBarShadowRectBuffer, mShadowPaint)
                i++
            }
        }

        // initialize the buffer
        val buffer = mBarBuffers[index]
        buffer.setPhases(phaseX, phaseY)
        buffer.setDataSet(index)
        buffer.setInverted(mChart.isInverted(dataSet.axisDependency))
        buffer.setBarWidth(mChart.barData.barWidth)

        buffer.feed(dataSet)

        trans.pointValuesToPixel(buffer.buffer)

        val isSingleColor = dataSet.colors.size == 1

        if (isSingleColor) {
            mRenderPaint.color = dataSet.color
        }

        var j = 0
        while (j < buffer.size()) {

            if (!mViewPortHandler.isInBoundsTop(buffer.buffer[j + 3]))
                break

            if (!mViewPortHandler.isInBoundsBottom(buffer.buffer[j + 1])) {
                j += 4
                continue
            }

            if (!isSingleColor) {
                // Set the color for the currently drawn value. If the index
                // is out of bounds, reuse colors.
                mRenderPaint.color = dataSet.getColor(j / 4)
            }

            c.drawRect(buffer.buffer[j], buffer.buffer[j + 1], buffer.buffer[j + 2],
                       buffer.buffer[j + 3], mRenderPaint)

            if (drawBorder) {
                c.drawRect(buffer.buffer[j], buffer.buffer[j + 1], buffer.buffer[j + 2],
                           buffer.buffer[j + 3], mBarBorderPaint)
            }
            j += 4
        }
    }

    override fun drawValues(c: Canvas) {
        if (!isDrawingValuesAllowed(mChart)) return

        val dataSets = mChart.barData.dataSets
        var posOffset = 0f
        var negOffset = 0f
        val isDrawValueAboveBar = mChart.isDrawValueAboveBarEnabled

        for (i in 0 until mChart.barData.dataSetCount) {
            val dataSet = dataSets[i]

            if (!shouldDrawValues(dataSet)) continue

            // apply the text-styling defined by the DataSet
            applyValueTextStyle(dataSet)

            val isInverted = mChart.isInverted(dataSet.axisDependency)
            val halfTextHeight = Utils.calcTextHeight(mValuePaint, "10") / 2f
            val buffer = mBarBuffers[i] // get the buffer
            val iconsOffset = MPPointF.getInstance(dataSet.iconsOffset).also {
                it.x = Utils.convertDpToPixel(it.x)
                it.y = Utils.convertDpToPixel(it.y)
            }

            // if only single values are drawn (sum)
            val offsets = if (!dataSet.isStacked) {
                drawValueForUnstacked(
                        dataSet = dataSet,
                        buffer = buffer,
                        i = i,
                        posOffset = posOffset,
                        negOffset = negOffset,
                        isInverted = isInverted,
                        c = c,
                        halfTextHeight = halfTextHeight,
                        iconsOffset = iconsOffset
                )
            } else {
                drawStacked(
                        dataSet = dataSet,
                        buffer = buffer,
                        formatter = dataSet.valueFormatter,
                        i = i,
                        posOffset = posOffset,
                        isDrawValueAboveBar = isDrawValueAboveBar,
                        valueOffsetPlus = valueOffsetPlus,
                        negOffset = negOffset,
                        isInverted = isInverted,
                        c = c,
                        halfTextHeight = halfTextHeight,
                        iconsOffset = iconsOffset
                )
            }

            negOffset = offsets.first
            posOffset = offsets.second

            MPPointF.recycleInstance(iconsOffset)
        }
    }

    private fun drawStacked(dataSet: IBarDataSet, buffer: BarBuffer, formatter: IValueFormatter, i: Int, posOffset: Float, isDrawValueAboveBar: Boolean, valueOffsetPlus: Float, negOffset: Float, isInverted: Boolean, c: Canvas?, halfTextHeight: Float, iconsOffset: MPPointF): Pair<Float, Float> {
        var posOffset1 = posOffset
        var negOffset1 = negOffset
        val transformer = mChart.getTransformer(dataSet.axisDependency)
        var bufferIndex = 0
        var index = 0

        while (index < dataSet.entryCount * mAnimator.phaseX) {

            val entry = dataSet.getEntryForIndex(index)
            val color = dataSet.getValueTextColor(index)
            val yVals = entry.yVals

            // we still draw stacked bars, but there is one non-stacked in between
            if (yVals == null) {

                if (!mViewPortHandler.isInBoundsTop(buffer.buffer[bufferIndex + 1]))
                    break

                if (!mViewPortHandler.isInBoundsX(buffer.buffer[bufferIndex]))
                    continue

                if (!mViewPortHandler.isInBoundsBottom(buffer.buffer[bufferIndex + 1]))
                    continue

                val `val` = entry.y
                val formattedValue = formatter.getFormattedValue(`val`,
                                                                 entry, i, mViewPortHandler)

                // calculate the correct offset depending on the draw position of the value
                val valueTextWidth = Utils.calcTextWidth(mValuePaint, formattedValue).toFloat()
                posOffset1 = if (isDrawValueAboveBar) valueOffsetPlus else -(valueTextWidth + valueOffsetPlus)
                negOffset1 = if (isDrawValueAboveBar) -(valueTextWidth + valueOffsetPlus) else valueOffsetPlus

                if (isInverted) {
                    posOffset1 = -posOffset1 - valueTextWidth
                    negOffset1 = -negOffset1 - valueTextWidth
                }

                if (dataSet.isDrawValuesEnabled) {
                    drawValue(c!!, // TODO remove
                              formattedValue,
                              buffer.buffer[bufferIndex + 2] + if (entry.y >= 0) posOffset1 else negOffset1,
                              buffer.buffer[bufferIndex + 1] + halfTextHeight, color
                    )
                }

                if (entry.icon != null && dataSet.isDrawIconsEnabled) {

                    val icon = entry.icon
                    var px = buffer.buffer[bufferIndex + 2] + if (entry.y >= 0) posOffset1 else negOffset1
                    var py = buffer.buffer[bufferIndex + 1]

                    px += iconsOffset.x
                    py += iconsOffset.y

                    Utils.drawImage(
                            c,
                            icon,
                            px.toInt(),
                            py.toInt(),
                            icon.intrinsicWidth,
                            icon.intrinsicHeight)
                }
            } else {

                val transformed = FloatArray(yVals.size * 2)

                var posY = 0f
                var negY = -entry.negativeSum

                run {
                    var k = 0
                    var idx = 0
                    while (k < transformed.size) {

                        val value = yVals[idx]
                        val y: Float

                        if (value == 0.0f && (posY == 0.0f || negY == 0.0f)) {
                            // Take care of the situation of a 0.0 value, which overlaps a non-zero bar
                            y = value
                        } else if (value >= 0.0f) {
                            posY += value
                            y = posY
                        } else {
                            y = negY
                            negY -= value
                        }

                        transformed[k] = y * mAnimator.phaseY
                        k += 2
                        idx++
                    }
                }

                transformer.pointValuesToPixel(transformed)

                var k = 0
                while (k < transformed.size) {

                    val `val` = yVals[k / 2]
                    val formattedValue = formatter.getFormattedValue(`val`,
                                                                     entry, i, mViewPortHandler)

                    // calculate the correct offset depending on the draw position of the value
                    val valueTextWidth = Utils.calcTextWidth(mValuePaint, formattedValue).toFloat()
                    posOffset1 = if (isDrawValueAboveBar) valueOffsetPlus else -(valueTextWidth + valueOffsetPlus)
                    negOffset1 = if (isDrawValueAboveBar) -(valueTextWidth + valueOffsetPlus) else valueOffsetPlus

                    if (isInverted) {
                        posOffset1 = -posOffset1 - valueTextWidth
                        negOffset1 = -negOffset1 - valueTextWidth
                    }

                    val drawBelow = `val` == 0.0f && negY == 0.0f && posY > 0.0f || `val` < 0.0f

                    val x = transformed[k] + if (drawBelow) negOffset1 else posOffset1
                    val y = (buffer.buffer[bufferIndex + 1] + buffer.buffer[bufferIndex + 3]) / 2f

                    if (!mViewPortHandler.isInBoundsTop(y))
                        break

                    if (!mViewPortHandler.isInBoundsX(x)) {
                        k += 2
                        continue
                    }

                    if (!mViewPortHandler.isInBoundsBottom(y)) {
                        k += 2
                        continue
                    }

                    if (dataSet.isDrawValuesEnabled) {
                        drawValue(c!!, // TODO: remove
                                  formattedValue, x, y + halfTextHeight, color)
                    }

                    if (entry.icon != null && dataSet.isDrawIconsEnabled) {

                        val icon = entry.icon

                        Utils.drawImage(
                                c,
                                icon,
                                (x + iconsOffset.x).toInt(),
                                (y + iconsOffset.y).toInt(),
                                icon.intrinsicWidth,
                                icon.intrinsicHeight)
                    }
                    k += 2
                }
            }

            bufferIndex = if (yVals == null) bufferIndex + 4 else bufferIndex + 4 * yVals.size
            index++
        }
        return Pair(negOffset1, posOffset1)
    }

    private fun drawValueForUnstacked(dataSet: IBarDataSet, buffer: BarBuffer, i: Int, posOffset: Float, negOffset: Float, isInverted: Boolean, c: Canvas, halfTextHeight: Float, iconsOffset: MPPointF): Pair<Float, Float> {
        var posOffset1 = posOffset
        var negOffset1 = negOffset
        var j = 0

        while (j < buffer.buffer.size * mAnimator.phaseX) {
            val y = (buffer.buffer[j + 1] + buffer.buffer[j + 3]) / 2f

//            if (!mViewPortHandler.isInBoundsTop(buffer.buffer[j + 1]))
//                break
//
//            if (!mViewPortHandler.isInBoundsX(buffer.buffer[j])) {
//                j += 4
//                continue
//            }
//
//            if (!mViewPortHandler.isInBoundsBottom(buffer.buffer[j + 1])) {
//                j += 4
//                continue
//            }

            val entry = dataSet.getEntryForIndex(j / 4)
            val value = entry.y
            val formattedValue = dataSet.valueFormatter.getFormattedValue(value, entry, i, mViewPortHandler)

            // calculate the correct offset depending on the draw position of the value
            val valueTextWidth = Utils.calcTextWidth(mValuePaint, formattedValue).toFloat()
            val offset = valueTextWidth / 2 + valueOffsetPlus
            val columnWidth = buffer.buffer[j+2] - buffer.buffer[j]

            if (isInverted) {
                posOffset1 = -posOffset1 - valueTextWidth
                negOffset1 = -negOffset1 - valueTextWidth
            }

            if (dataSet.isDrawValuesEnabled) {
                drawValue(
                        c,
                        formattedValue,
                        buffer.buffer[j + 2] + if (columnWidth < valueTextWidth) offset else offset * -1,
                        y + halfTextHeight,
                        dataSet.getValueTextColor(j / 2)
                )
            }

            if (entry.icon != null && dataSet.isDrawIconsEnabled) {
                drawIcon(entry, buffer, j, value, posOffset1, negOffset1, y, iconsOffset, c)
            }

            j += 4
        }

        return Pair(negOffset1, posOffset1)
    }

    private fun drawValue(c: Canvas, valueText: String, x: Float, y: Float, color: Int) {
        mValuePaint.color = color
        c.drawText(valueText, x, y, mValuePaint)
    }

    override fun prepareBarHighlight(x: Float, y1: Float, y2: Float, barWidthHalf: Float, trans: Transformer) {
        val top = x - barWidthHalf
        val bottom = x + barWidthHalf

        // left, top, right, bottom
        mBarRect.set(y1, top, y2, bottom)

        trans.rectToPixelPhaseHorizontal(mBarRect, mAnimator.phaseY)
    }

    override fun setHighlightDrawPos(high: Highlight, bar: RectF) {
        high.setDraw(bar.centerY(), bar.right)
    }

    override fun isDrawingValuesAllowed(chart: ChartInterface) =
            chart.data.entryCount < chart.maxVisibleCount * mViewPortHandler.scaleY

    private fun drawIcon(entry: BarEntry, buffer: BarBuffer, j: Int, value: Float, posOffset1: Float, negOffset1: Float, y: Float, iconsOffset: MPPointF, c: Canvas?) {
        val icon = entry.icon
        var px = buffer.buffer[j + 2] + if (value >= 0) posOffset1 else negOffset1
        var py = y

        px += iconsOffset.x
        py += iconsOffset.y

        Utils.drawImage(c, icon, px.toInt(), py.toInt(), icon.intrinsicWidth, icon.intrinsicHeight)
    }
}