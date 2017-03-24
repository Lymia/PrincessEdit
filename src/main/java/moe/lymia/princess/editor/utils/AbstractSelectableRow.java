/*
 * Copyright (C) 2005 David Orme <djo@coconut-palm-software.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     David Orme     - Initial API and implementation
 *     Elias Volanakis - 267316
 */

package moe.lymia.princess.editor.utils;

import org.eclipse.nebula.widgets.compositetable.CompositeTable;
import org.eclipse.nebula.widgets.compositetable.IRowContentProvider;
import org.eclipse.nebula.widgets.compositetable.IRowFocusListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.*;

import java.util.ArrayList;
import java.util.List;

/**
 * An abstract row class making it easy to implement a table where the whole row is selected at once.
 *
 * This version has been modified to add the functionality required for PrincessEdit's main card list.
 *
 * @author djo
 * @author Lymia
 */
abstract class AbstractSelectableRow extends Composite implements
        TraverseListener, FocusListener, MouseListener, IRowFocusListener, IRowContentProvider {

    private Display display = Display.getCurrent();

    private Color WIDGET_BACKGROUND = display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
    private Color WIDGET_FOREGROUND = display.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);
    private Color LIST_BACKGROUND = display.getSystemColor(SWT.COLOR_LIST_BACKGROUND);
    private Color LIST_FOREGROUND = display.getSystemColor(SWT.COLOR_LIST_FOREGROUND);
    private Color LIST_SELECTION = display.getSystemColor(SWT.COLOR_LIST_SELECTION);
    private Color LIST_SELECTION_TEXT = display.getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT);
    private Color LIST_SELECTION_NOFOCUS = display.getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW);
    private Color LIST_SELECTION_TEXT_NOFOCUS = display.getSystemColor(SWT.COLOR_LIST_FOREGROUND);

    public AbstractSelectableRow(Composite parent, int style) {
        super(parent, style);
        addTraverseListener(this);
        addFocusListener(this);
        addMouseListener(this);
    }

    /**
     * This method initializes this
     */
    private void initialize() {
        this.labels = new ArrayList<>();
        for (int i = 0; i < getColumnCount(); i++) {
            Label label = new Label(this, SWT.NONE);
            this.labels.add(label);
            label.addMouseListener(this);
        }
    }

    private Object model = null;

    public void setModel(Object object) {
        this.model = object;
    }

    public Object getModel() {
        return model;
    }

    public void setMenu(Menu menu) {
        super.setMenu(menu);
        for (Label label : labels) label.setMenu(menu);
    }

    // Row color ---------------------------------------------------------------

    protected void setRowColor(Color foreground, Color background) {
        setBackground(background);
        setForeground(foreground);
        for (Control aChildren : getChildren()) {
            aChildren.setBackground(background);
            aChildren.setForeground(foreground);
        }
    }

    protected void setWidgetColor() {
        setRowColor(WIDGET_FOREGROUND, WIDGET_BACKGROUND);
    }

    protected void setSelectedColor() {
        setRowColor(LIST_SELECTION_TEXT, LIST_SELECTION);
    }

    protected void setInactiveSelectedColor() {
        setRowColor(LIST_SELECTION_TEXT_NOFOCUS, LIST_SELECTION_NOFOCUS);
    }

    protected void setRowColor() {
        setRowColor(LIST_FOREGROUND, LIST_BACKGROUND);
    }


    // Labels list -------------------------------------------------------------

    protected List<Label> labels;
    private int columnCount = -1;

    public List<Label> getLabelsList() {
        return this.labels;
    }

    /**
     * Method setColumnCount.  Sets the number of columns in the row.  This
     * method must be called <b>exactly</b> once in the overridden constructor.
     *
     * @param columnCount The number of columns in the row.
     */
    public void setColumnCount(int columnCount) {
        if (this.columnCount > -1) {
            throw new IllegalArgumentException("Cannot setColumnCount more than once");
        }
        this.columnCount = columnCount;
        initialize();
        setRowColor();
    }

    // Event handlers ----------------------------------------------------------

    public void keyTraversed(TraverseEvent e) {
        // NOOP: this just lets us receive focus from SWT
    }

    protected void setSelection(Object model) { }

    public void focusGained(FocusEvent e) {
        setSelectedColor();
        selected = true;
        setSelection(model);
    }

    private boolean selected = false;
    private boolean inactiveSelected = false;

    public void focusLost(FocusEvent e) {
        if (selected) {
            setInactiveSelectedColor();
            inactiveSelected = true;
        }
    }

    private void deselectRow() {
        setRowColor();
        selected = false;
        inactiveSelected = false;
    }

    public void depart(CompositeTable sender, int currentObjectOffset, Control row) {
        if (row == this && selected) {
            deselectRow();
        }
    }

    public void arrive(CompositeTable sender, int currentObjectOffset, Control newRow) {
        // NO OP
    }

    public void refresh(CompositeTable sender, int currentObjectOffset, Control row) {
        if (row == this && inactiveSelected) {
            deselectRow();
        }
    }

    public boolean requestRowChange(CompositeTable sender,
                                    int currentObjectOffset, Control row) {
        // Always ok to change rows
        return true;
    }

    public void mouseDown(MouseEvent e) {
        setFocus();
    }

    public void mouseDoubleClick(MouseEvent e) { }

    public void mouseUp(MouseEvent e) { }

    private int getColumnCount() {
        return columnCount;
    }

}