/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package org.teiid.query.processor.relational;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.teiid.common.buffer.BlockedException;
import org.teiid.common.buffer.BufferManager;
import org.teiid.common.buffer.BufferManagerFactory;
import org.teiid.common.buffer.TupleBatch;
import org.teiid.core.TeiidComponentException;
import org.teiid.core.TeiidProcessingException;
import org.teiid.core.types.DataTypeManager;
import org.teiid.query.processor.FakeDataManager;
import org.teiid.query.sql.symbol.ElementSymbol;
import org.teiid.query.util.CommandContext;


/**
 */
public class TestUnionAllNode {

    public void helpTestUnion(RelationalNode[] children, RelationalNode union, List[] expected) throws TeiidComponentException, TeiidProcessingException {
        BufferManager mgr = BufferManagerFactory.getTestBufferManager(1, 2);
        CommandContext context = new CommandContext("pid", "test", null, null, 1);               //$NON-NLS-1$ //$NON-NLS-2$
        FakeDataManager fdm = new FakeDataManager();
        for(int i=0; i<children.length; i++) {
            union.addChild(children[i]);
            children[i].initialize(context, mgr, fdm);
        }
        
        union.initialize(context, mgr, fdm);
        
        union.open();
        
        int currentRow = 1;
        while(true) {
            try {
                TupleBatch batch = union.nextBatch();
                for(int row = currentRow; row <= batch.getEndRow(); row++) {
                    List tuple = batch.getTuple(row);
                    //System.out.println(tuple);
                    assertEquals("Rows don't match at " + row, expected[row-1], tuple); //$NON-NLS-1$
                }
                
                currentRow += batch.getRowCount();    

                if(batch.getTerminationFlag()) {
                    break;
                }
            } catch(BlockedException e) {
                // ignore and retry
            }
        }
        
        union.close();
        
        assertEquals("Didn't match expected counts", expected.length, currentRow-1); //$NON-NLS-1$
    }
    
    @Test public void testNoRows() throws TeiidComponentException, TeiidProcessingException {
        ElementSymbol es1 = new ElementSymbol("e1"); //$NON-NLS-1$
        es1.setType(DataTypeManager.DefaultDataClasses.INTEGER);

        ElementSymbol es2 = new ElementSymbol("e2"); //$NON-NLS-1$
        es2.setType(DataTypeManager.DefaultDataClasses.INTEGER);

        List leftElements = new ArrayList();
        leftElements.add(es1);
        RelationalNode leftNode = new FakeRelationalNode(1, new List[0]);
        leftNode.setElements(leftElements);

        List rightElements = new ArrayList();
        rightElements.add(es2);
        RelationalNode rightNode = new FakeRelationalNode(2, new List[0]);
        rightNode.setElements(rightElements);
        
        List unionElements = new ArrayList();
        unionElements.add(es1);

        UnionAllNode union = new UnionAllNode(3);
        union.setElements(unionElements);
        
        helpTestUnion(new RelationalNode[] {leftNode, rightNode}, union, new List[0]);        
    }

    public void helpTestUnionConfigs(int sources, int blockModIndex, int rowsPerSource, int batchSize, List[] expected) throws TeiidComponentException, TeiidProcessingException {
        ElementSymbol es1 = new ElementSymbol("e1"); //$NON-NLS-1$
        es1.setType(DataTypeManager.DefaultDataClasses.INTEGER);

        ElementSymbol es2 = new ElementSymbol("e2"); //$NON-NLS-1$
        es2.setType(DataTypeManager.DefaultDataClasses.INTEGER);

        RelationalNode[] nodes = new RelationalNode[sources];
        for(int i=0; i<nodes.length; i++) {
            List childElements = new ArrayList();
            childElements.add(es1);
            
            // Build source data
            List[] tuples = new List[rowsPerSource];
            for(int r = 0; r<rowsPerSource; r++) {
                tuples[r] = Arrays.asList(new Object[] { new Integer(i) });
            }
            
            if(blockModIndex >= 0 && (i % blockModIndex == 0)) {
                nodes[i] = new BlockingFakeRelationalNode(i, tuples, batchSize);
            } else {                
                nodes[i] = new FakeRelationalNode(i, tuples, batchSize);
            }
            nodes[i].setElements(childElements);           
        }
        
        List unionElements = new ArrayList();
        unionElements.add(es1);

        UnionAllNode union = new UnionAllNode(nodes.length);
        union.setElements(unionElements);
        
        helpTestUnion(nodes, union, expected);           
    }
    
    @Test public void testBasicUnion() throws TeiidComponentException, TeiidProcessingException {
        List expected[] = new List[] {
            Arrays.asList(new Object[] { new Integer(0) }),    
            Arrays.asList(new Object[] { new Integer(0) }),    
            Arrays.asList(new Object[] { new Integer(1) }),
            Arrays.asList(new Object[] { new Integer(1) })
          
        };

        helpTestUnionConfigs(2, -1, 2, 50, expected);
        
    }

    @Test public void testBasicUnionMultipleSources() throws TeiidComponentException, TeiidProcessingException {
        List expected[] = new List[] {
            Arrays.asList(new Object[] { new Integer(0) }),    
            Arrays.asList(new Object[] { new Integer(0) }),    
            Arrays.asList(new Object[] { new Integer(1) }),
            Arrays.asList(new Object[] { new Integer(1) }),    
            Arrays.asList(new Object[] { new Integer(2) }),    
            Arrays.asList(new Object[] { new Integer(2) }),    
            Arrays.asList(new Object[] { new Integer(3) }),
            Arrays.asList(new Object[] { new Integer(3) }),    
            Arrays.asList(new Object[] { new Integer(4) }),    
            Arrays.asList(new Object[] { new Integer(4) })          
        };

        helpTestUnionConfigs(5, -1, 2, 50, expected);
    }

    @Test public void testMultipleSourcesHalfBlockingNodes() throws TeiidComponentException, TeiidProcessingException  {
        List expected[] = new List[] {
            Arrays.asList(new Object[] { new Integer(1) }),    
            Arrays.asList(new Object[] { new Integer(0) }),    
            Arrays.asList(new Object[] { new Integer(3) }),    
            Arrays.asList(new Object[] { new Integer(2) }),
            Arrays.asList(new Object[] { new Integer(4) })          
        };

        helpTestUnionConfigs(5, 2, 1, 50, expected);
    }
    
    @Test public void testMultipleSourcesAllBlockingNodes() throws TeiidComponentException, TeiidProcessingException {
        List expected[] = new List[] {
            Arrays.asList(new Object[] { new Integer(0) }),    
            Arrays.asList(new Object[] { new Integer(1) }),    
            Arrays.asList(new Object[] { new Integer(2) }),    
            Arrays.asList(new Object[] { new Integer(3) }),
            Arrays.asList(new Object[] { new Integer(4) })          
        };

        helpTestUnionConfigs(5, 1, 1, 50, expected);       
    }    
    
    @Test public void testMultipleSourceMultiBatchAllBlocking() throws TeiidComponentException, TeiidProcessingException {
        List expected[] = new List[] {
            Arrays.asList(new Object[] { new Integer(0) }),    
            Arrays.asList(new Object[] { new Integer(1) }),    
            Arrays.asList(new Object[] { new Integer(0) }),    
            Arrays.asList(new Object[] { new Integer(2) }),    
            Arrays.asList(new Object[] { new Integer(0) }),    
            Arrays.asList(new Object[] { new Integer(1) }),    

            Arrays.asList(new Object[] { new Integer(0) }),    
            Arrays.asList(new Object[] { new Integer(0) }),    
            Arrays.asList(new Object[] { new Integer(1) }),    
            Arrays.asList(new Object[] { new Integer(0) }),    
            Arrays.asList(new Object[] { new Integer(2) }),    
            Arrays.asList(new Object[] { new Integer(1) }),    

            Arrays.asList(new Object[] { new Integer(1) }),    
            Arrays.asList(new Object[] { new Integer(2) }),    
            Arrays.asList(new Object[] { new Integer(1) }),    
            Arrays.asList(new Object[] { new Integer(2) }),    
            Arrays.asList(new Object[] { new Integer(2) }),    
            Arrays.asList(new Object[] { new Integer(2) })          
        };

        helpTestUnionConfigs(3, 1, 6, 1, expected);       
    }    

}
