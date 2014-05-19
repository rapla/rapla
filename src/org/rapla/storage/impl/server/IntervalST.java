package org.rapla.storage.impl.server;

import java.util.LinkedList;

import org.rapla.entities.domain.AppointmentBlock;

/**
 * LICENSE  GNU General Public License, version 3 (GPLv3)
 * Modified version of the the IntervalST tree from the algorithms book from Robert Sedgewick and Kevin Wayne. 
 */
public class IntervalST  {

    private Node root;   // root of the BST

    // BST helper node data type
    private class Node {
        AppointmentBlock interval;      // key
        Node left, right;         // left and right subtrees
        int N;                    // size of subtree rooted at this node
        long max;                  // max endpoint in subtree rooted at this node

        Node(AppointmentBlock interval) {
            this.interval = interval;
            this.N        = 1;
            this.max      = interval.getEnd();
        }
    }

   /*************************************************************************
    *  randomized insertion
    *************************************************************************/
    public void put(AppointmentBlock interval) {
//        if (contains(interval)) {remove(interval);  }
        root = randomizedInsert(root, interval);
    }

    // make new node the root with uniform probability
    private Node randomizedInsert(Node x, AppointmentBlock interval) {
        if (x == null) return new Node(interval);
        if (Math.random() * size(x) < 1.0) return rootInsert(x, interval);
        int cmp = interval.compareTo(x.interval);
        if (cmp < 0)  x.left  = randomizedInsert(x.left,  interval);
        else          x.right = randomizedInsert(x.right, interval);
        fix(x);
        return x;
    }

    private Node rootInsert(Node x, AppointmentBlock interval) {
        if (x == null) return new Node(interval);
        int cmp = interval.compareTo(x.interval);
        if (cmp < 0) { x.left  = rootInsert(x.left,  interval); x = rotR(x); }
        else         { x.right = rootInsert(x.right, interval); x = rotL(x); }
        return x;
    }


   /*************************************************************************
    *  Interval searching
    *************************************************************************/


    // return *all* intervals in data structure that intersect the given interval
    // running time is proportional to R log N, where R is the number of intersections
    public Iterable<AppointmentBlock> searchAll(AppointmentBlock interval) {
        LinkedList<AppointmentBlock> list = new LinkedList<AppointmentBlock>();
        searchAll(root, interval, list);
        return list;
    }

    // look in subtree rooted at x
    public boolean searchAll(Node x, AppointmentBlock interval, LinkedList<AppointmentBlock> list) {
         boolean found1 = false;
         boolean found2 = false;
         boolean found3 = false;
         if (x == null)
            return false;
        if (interval.intersects(x.interval)) {
            list.add(x.interval);
            found1 = true;
        }
        long start = interval.getStart();
        if (x.left != null && x.left.max >= start)
            found2 = searchAll(x.left, interval, list);
        if (found2 || x.left == null || x.left.max < start)
            found3 = searchAll(x.right, interval, list);
        return found1 || found2 || found3;
    }


   /*************************************************************************
    *  useful binary tree functions
    *************************************************************************/

    // return number of nodes in subtree rooted at x
    public int size() { return size(root); }
    private int size(Node x) { 
        if (x == null) return 0;
        else           return x.N;
    }

    // height of tree (empty tree height = 0)
    public int height() { return height(root); }
    private int height(Node x) {
        if (x == null) return 0;
        return 1 + Math.max(height(x.left), height(x.right));
    }


   /*************************************************************************
    *  helper BST functions
    *************************************************************************/
    // fix auxilliar information (subtree count and max fields)
    private void fix(Node x) {
        if (x == null) return;
        x.N = 1 + size(x.left) + size(x.right);
        x.max = max3(x.interval.getEnd(), max(x.left), max(x.right));
    }

    private long max(Node x) {
        if (x == null) return Long.MIN_VALUE;
        return x.max;
    }

    // precondition: a is not null
    private long max3(long a, long b, long c) {
        return Math.max(a, Math.max(b, c));
    }

    // right rotate
    private Node rotR(Node h) {
        Node x = h.left;
        h.left = x.right;
        x.right = h;
        fix(h);
        fix(x);
        return x;
    }

    // left rotate
    private Node rotL(Node h) {
        Node x = h.right;
        h.right = x.left;
        x.left = h;
        fix(h);
        fix(x);
        return x;
    }

}