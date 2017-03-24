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

package moe.lymia.nebula.compositetable;

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
public abstract class AbstractEditorSelectableRow
        extends Composite
        implements TraverseListener, FocusListener, MouseListener, IRowFocusListener, IRowContentProvider {

    public AbstractEditorSelectableRow(Composite parent, int style) {
        super(parent, style);
        addTraverseListener(this);
        addFocusListener(this);
        addMouseListener(this);
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
        for (Control label : controlList) label.setMenu(menu);
    }

    // Row color ---------------------------------------------------------------

    private Display display = Display.getCurrent();

    private Color WIDGET_BACKGROUND = display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
    private Color WIDGET_FOREGROUND = display.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);
    private Color LIST_BACKGROUND = display.getSystemColor(SWT.COLOR_LIST_BACKGROUND);
    private Color LIST_FOREGROUND = display.getSystemColor(SWT.COLOR_LIST_FOREGROUND);
    private Color LIST_SELECTION = display.getSystemColor(SWT.COLOR_LIST_SELECTION);
    private Color LIST_SELECTION_TEXT = display.getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT);
    private Color LIST_SELECTION_NOFOCUS = display.getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW);
    private Color LIST_SELECTION_TEXT_NOFOCUS = display.getSystemColor(SWT.COLOR_LIST_FOREGROUND);

    protected void setRowColor(Color foreground, Color background) {
        setBackground(background);
        setForeground(foreground);
        if(!editorModeActive) for (Control aChildren : getControlList()) {
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

    protected void setColorByState() {
        if(editorModeActive) setWidgetColor();
        else if(selected && inactiveSelected) setInactiveSelectedColor();
        else if(selected) setSelectedColor();
        else setRowColor();
    }

    // Labels list -------------------------------------------------------------

    private List<Control> controlList = new ArrayList<>();
    private int columnCount = -1;

    protected List<Control> getControlList() {
        return this.controlList;
    }

    protected void registerControl(Control control) {
        this.controlList.add(control);
        control.addMouseListener(this);
    }

    protected void makeColumnControl(int i) {
        Label l = new Label(this, SWT.NONE);
        registerControl(l);
    }

    private void initialize() {
        for (int i = 0; i < getColumnCount(); i++) makeColumnControl(i);
    }

    /**
     * Sets the number of columns in the row.  This method must be called <b>exactly</b> once in the makeTableMode().
     *
     * @param columnCount The number of columns in the row.
     */
    protected void setColumnCount(int columnCount) {
        if (this.columnCount > -1) {
            throw new IllegalArgumentException("Cannot setColumnCount more than once");
        }
        this.columnCount = columnCount;
        initialize();
        setColorByState();
    }

    // Editor mode -------------------------------------------------------------

    private boolean editorModeActive = false;

    public boolean isEditorModeActive() {
        return editorModeActive;
    }

    protected boolean canEnableEditorMode() { return true; }

    protected void makeTableMode() { }
    protected void makeEditorMode() { }

    protected void setEditorMode(boolean active) {
        active &= canEnableEditorMode();

        editorModeActive = active;

        for(Control control : getChildren()) control.dispose();
        columnCount = -1;
        controlList.clear();

        if(active) {
            makeEditorMode();
            setColorByState();
        } else {
            makeTableMode();
            setColorByState();
        }

        this.layout(true);
    }

    // Event handlers ----------------------------------------------------------

    public void keyTraversed(TraverseEvent e) {
        // NOOP: this just lets us receive focus from SWT
    }

    protected void setSelection(Object model) { }

    public void focusGained(FocusEvent e) {
        if(!editorModeActive) {
            selected = true;
            setColorByState();

            setSelection(model);
        }
    }

    private boolean selected = false;
    private boolean inactiveSelected = false;

    public void focusLost(FocusEvent e) {
        if(!editorModeActive && selected) {
            inactiveSelected = true;
            setColorByState();
        }
    }

    private void deselectRow() {
        if(editorModeActive) setEditorMode(false);

        selected = false;
        inactiveSelected = false;
        setColorByState();
    }

    public void depart(CompositeTable sender, int currentObjectOffset, Control row) {
        if (row == this && selected) deselectRow();
    }

    public void arrive(CompositeTable sender, int currentObjectOffset, Control newRow) {
        // NO OP
    }

    public void refresh(CompositeTable sender, int currentObjectOffset, Control row) {
        if (row == this && editorModeActive) setEditorMode(false);
        if (row == this && inactiveSelected) deselectRow();
    }

    public boolean requestRowChange(CompositeTable sender,
                                    int currentObjectOffset, Control row) {
        // Always ok to change rows
        return true;
    }

    public void mouseDown(MouseEvent e) {
        setFocus();
    }

    public void mouseDoubleClick(MouseEvent e) {
        if(!editorModeActive) setEditorMode(true);
    }

    public void mouseUp(MouseEvent e) { }

    private int getColumnCount() {
        return columnCount;
    }
}