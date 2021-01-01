// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.makeparallel.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static java.lang.Math.PI;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.ChangeNodesCommand;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.MoveCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmDataManager;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Utils;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.tools.Geometry;




/**
 * MakeParallel
 * Make segments of a way Parallel to each other.
 *
 * @author JJWW
 */
public class MakeParallelAction extends JosmAction {


  @Override
  protected void updateEnabledState() {
    //if (getLayerManager().getEditDataSet() == null) {
    //    setEnabled(false);
    //} else
    //    updateEnabledState(getLayerManager().getEditDataSet().getSelected());
    updateEnabledStateOnCurrentSelection();
  }

  @Override
  protected void updateEnabledState(Collection<? extends OsmPrimitive> selection) {
    if (selection == null) {
      setEnabled(false);
      return;
    }
    if (selection.size() == 4 ) {
      setEnabled(true);
    }
  }

  public MakeParallelAction() {
      super(
              tr("MakeParallel"),
              "dialogs/logo-makeparallel.png",
              tr("Make segments parallel"),
              Shortcut.registerShortcut("menu:makeparallel",
                      tr("Menu: {0}", tr("MakeParallel")),
                      KeyEvent.VK_L, Shortcut.NONE),
              false
              );
  }


  /** Returns true if the list of nodes form a closed path.
  * @param nodelist The list of nodes
  * @return True if closed path
  */
  public boolean isClosedPath(List<Node> nodelist) {
    return nodelist.get(0) == nodelist.get(nodelist.size() - 1);
  }

  /** Checks if a node is special and should not be deleted.
  * That is if a node has tags on it or is part of multiple ways
  * @param node The node
  * @return True if special
  */
  public boolean isSpecialNode(Node node) {
    boolean special = false;

    if (! node.getKeys().isEmpty()) {
      Logging.warn("makeparallel: node is special (hasKey) " + node);
      special = true;
    }
     
    List<Way> w = node.getParentWays();
    if (w.size() > 1) {
      Logging.warn("makeparallel: node is special (has multiple parentways) " + node);
      special = true;
    }

    return special;
  }

  /**
   * Called when the action is executed, typically with keyboard shortcut.
   *
   * This method looks at what is selected and performs the action
   */
  @Override
  public void actionPerformed(ActionEvent e) {

    //Figure out what we have to work with:
    Collection<OsmPrimitive> selection = getLayerManager().getEditDataSet().getSelected();
    List<Node> selectedNodes = new ArrayList<>(Utils.filteredCollection(selection, Node.class));
    // List<Way> selectedWays = new ArrayList<>(Utils.filteredCollection(selection, Way.class));

    List<Node> s1; // source segment on w1
    List<Node> s2; // destinaton segment on w2

    if (SanityCheck(selectedNodes)) { 
      s1 = getSegmentFromWay(selectedNodes.get(0), selectedNodes.get(2));
      s2 = getSegmentFromWay(selectedNodes.get(1), selectedNodes.get(3));

      // Add or remove nodes to segment s2 to match s1
      if (s1.size() > 3 && s2.size() > 3) {
        if (addOrRemoveNodesToMatchSegments(s1, s2) ) { // if successfully added/removed nodes
          double d = calculateDistance(s1, s2);
          makeSegmentsParallel(s1, s2, d); // Make segment s2 Parallel to s1 at distance d
        }
      }
      else if (s1.size() > 0 && s2.size() > 0) {
        new Notification(
           tr("Source segment must be at least 3 nodes long"))
           .setIcon(JOptionPane.INFORMATION_MESSAGE)
           .setDuration(Notification.TIME_LONG)
           .show();
         return;
      }
    
    } // if correctly selected nodes
    
    MainApplication.getMap().mapView.repaint();
  }  // actionPerformed()

  /** Checks if the input is as expected.
  * @param selection The selected nodes.
  * @return True if input is as expected, false otherwise.
  */
  private boolean SanityCheck(List<Node> selection) {
    if (selection.size() != 4) { // FIXME more sanity checks?
      new Notification(
         tr("Please select exactly four nodes on two different ways"))
         .setIcon(JOptionPane.INFORMATION_MESSAGE)
         .setDuration(Notification.TIME_LONG)
         .show();
       return false;
    }
    else if (selection.get(0).getParentWays().get(0) != selection.get(2).getParentWays().get(0)) {
      new Notification(
         tr("First and third selected node must be on same way"))
         .setIcon(JOptionPane.INFORMATION_MESSAGE)
         .setDuration(Notification.TIME_LONG)
         .show();
       return false;
    }
    else if (selection.get(1).getParentWays().get(0) != selection.get(3).getParentWays().get(0)) {
      new Notification(
         tr("Second and fourth selected node must be on same way"))
         .setIcon(JOptionPane.INFORMATION_MESSAGE)
         .setDuration(Notification.TIME_LONG)
         .show();
       return false;
    }


    return true;
  } // SanityCheck 

  /** Traverses the way that node n1 belongs to until it reaches n2 and returns the segment. 
  * Both nodes should be on the same way. The nodes should have single parent way.
  * @param n1 the node on the way to mark the start of the segment
  * @param n2 the node on the way to mark the end of the segment
  * @return The list of nodes that make the segment
  */
  private List<Node> getSegmentFromWay(Node n1, Node n2) {
    List<Way> w; // way
    List<Node> s = new ArrayList<>(); // build our segment of Nodes on the Way

    w = n1.getParentWays(); // get parent way

    //what if node has multiple ways? -> we don't do anything
    if (w.size() > 1) {
      Logging.warn("makeparallel: start node has multiple parents");
      return new ArrayList<Node>();
    }

    if (w.get(0).containsNode(n2) ) { // check selected both nodes are on same way

      List<Node> nw = w.get(0).getNodes(); // get nodes from parent way
      
      if (isClosedPath(nw)) {
        // segment is part of area
        Logging.warn("makeparallel: segment is from area with " + nw.size() + " nodes");
        // FIXME area's are screwed up if nw.get(0) is in the middle of selected segment
        // --> will get detected as reversed way
      }
      else {
        // segment is part of way
        Logging.warn("makeparallel: segment is from way with " + nw.size() + " nodes");
      } // else 

        boolean addnodestosegment = false;
        for (int i = 0; i < nw.size(); i++) {
          if ( nw.get(i) == n1 ) {
            addnodestosegment = true;
          }
          if ( nw.get(i) == n2 && addnodestosegment == false) {
            // we encounter endpoint first, way is wrong way around/ or an area with "rootnode" in segment
            Logging.warn("makeparallel: way is wrong direction, try to reverse"); 
            new Notification(
               tr("Way is in wrong direction, try to reverse"))
               .setIcon(JOptionPane.INFORMATION_MESSAGE)
               .setDuration(Notification.TIME_LONG)
               .show();
             return new ArrayList<Node>();
          }
          if ( nw.get(i) == n2 ) {
            s.add(nw.get(i));
            addnodestosegment = false;
          }
        
          if (addnodestosegment == true )  {
            s.add(nw.get(i));
          }
        } // for

    }
    else {
      Logging.warn("makeparallel: nodes not on same way!!!");
    }

    return s;
  } // end getSegmentFromWay()

  /** Calculates the the distance for makeSegmentsParallel 
  * @param s1 The source segment to make parallel
  * @param s2 The destination segment to make parallel
  * @return The distance in the correct direction (hopefully)
  */
  private double calculateDistance(List<Node> s1, List<Node> s2) {
    // Get our distance d
    double d1 = s1.get(0).getEastNorth().distance( s2.get(0).getEastNorth() );

    double heading_s1 = s1.get(0).getEastNorth().heading(s1.get(1).getEastNorth());
    double heading_to_s2 = s1.get(0).getEastNorth().heading(s2.get(0).getEastNorth());
    //Logging.warn("makeparallel: heading_s1=" + heading_s1);
    //Logging.warn("makeparallel: heading_s2=" + heading_to_s2);
    
    int factor;
    double left =  heading_to_s2 - heading_s1;
    double right =  heading_s1 - heading_to_s2;
    if (left < 0)  left  += PI;
    if (right < 0) right += PI;
    //Logging.warn("makeparallel: left=" + left);
    //Logging.warn("makeparallel: right=" + right);

    if (left < right) {
     factor = -1;
    }
    else {
     factor = 1;
    }

    //Logging.warn("makeparallel: factor=" + factor);
    double d = factor * d1;
    Logging.warn("makeparallel: distance=" + d);

    return d;
  } // end calculateDistance()

  /** Adds or Removes nodes from the destination segment s2 to match the number of nodes in source s1.
  * @param s1 The source segment
  * @param s2 The destination segment
  * @return true if successful
  */
  private boolean addOrRemoveNodesToMatchSegments(List<Node> s1, List<Node> s2) {
    int diff = s1.size() - s2.size();
    DataSet ds = OsmDataManager.getInstance().getEditDataSet();
    List<Command> cmds = new ArrayList<>(); // to store our sequence of commands

    List<Way> w; // way
    w = s2.get(0).getParentWays(); // get parent way
    List<Node> nw = w.get(0).getNodes(); // get nodes from parent way

    Logging.warn("makeparallel: s1.size=" + s1.size());
    Logging.warn("makeparallel: s2.size=" + s2.size());
    boolean success = true;

    if (diff > 0 && s1.size() > 2 && s2.size() > 2) { // add nodes to s2
      Logging.warn("makeparallel: adding nodes to s2: " + diff);
      // find offset
      int offset = 0;
      for (int i = 0; i < w.get(0).getNodesCount(); i++) {
        if (nw.get(i) == s2.get(s2.size()-1)) { // this node on the way matches our previous to last node in segment
          offset = i; // this is our offset to insert new nodes
        }  
      }
      Logging.warn("makeparallel: adding nodes to s2: offset " + offset);
      
      int offset_segment = s2.size() - 1;
      while (diff > 0) {
        Node n = new Node();
        n.setEastNorth(w.get(0).getNode(offset).getEastNorth());

        //ds.addPrimitive(n);            // add new node to dataset
        cmds.add(new AddCommand(ds, n)); // perform actions undoable

        //w.get(0).addNode(offset, n); // add new node to way
        nw.add(offset, n);
        //cmds.add(new ChangeNodesCommand(w.get(0), nw)); // perform actions undoable -> outside whileloop

        s2.add(offset_segment, n);   // add new node to our segment 

        diff--;
      } // while
      cmds.add(new ChangeNodesCommand(w.get(0), nw)); // perform actions undoable
	    UndoRedoHandler.getInstance().add(new SequenceCommand("Make Parallel: add nodes", cmds));

    } // if
    else { // remove nodes from s2
      if (diff < 0) { 
        Logging.warn("makeparallel: removing nodes from s2: " + diff);

        // find offset
        int offset = 0;
        for (int i = 0; i < w.get(0).getNodesCount(); i++) {
          if (nw.get(i) == s2.get(s2.size()-1)) { // this node on the way matches our last node in segment
            offset = i; // this is our offset to delete nodes
          }  
        }
        //Logging.warn("makeparallel: deleting nodes from s2: offset " + offset);

        List<Node> nodes2delete = new ArrayList<>();
        while (diff < 0) {
          Node n = w.get(0).getNode(offset);
          //Logging.warn("makeparallel: deleting nodes from s2: " + n);

          nodes2delete.add(n);
          if (isSpecialNode(n)) {
            // check if special tags on node
            //abort
            new Notification(
               tr("This will delete a node with tags or node that belongs to multiple ways. Aborting."))
               .setIcon(JOptionPane.ERROR_MESSAGE)
               .setDuration(Notification.TIME_LONG)
               .show();
             success = false;
          } //if 
          
          //w.get(0).removeNode(n); // remove node from way
          nw.remove(offset);

          s2.remove(s2.size()-1);

          //ds.removePrimitive(n);
          //cmds.add(new DeleteCommand(ds, n)); // perform actions undoable -> outside whileloop

          diff++;
          offset--;
        }  // while
        if (success) {
          cmds.add(new ChangeNodesCommand(w.get(0), nw)); // perform actions undoable
          cmds.add(new DeleteCommand(ds, nodes2delete)); // perform actions undoable
        
	        UndoRedoHandler.getInstance().add(new SequenceCommand("Make Parallel: remove nodes", cmds));
        }
      } //  if
    } // else

    return success;

  } // addOrRemoveNodesToMatchSegments()


  /** Move the nodes from segment s2 to be parallel to s1 at distance d.
  * This is the main function that makes the segments Paralell
  *
  * @param s1 containing nodes from source segment
  * @param s2 containing nodes from destination segment
  * @param d the distance
  */
  private void makeSegmentsParallel (List<Node> s1, List<Node> s2, double d) {
    List<Command> cmds = new ArrayList<>(); // to store our sequence of commands
    // Caculations from ParallelWays:
    // https://josm.openstreetmap.de/browser/josm/trunk/src/org/openstreetmap/josm/actions/mapmode/ParallelWays.java

    if (s1.size() > 2 && s2.size() > 2) { 
      // Initialize the required parameters. (segment normals, etc.)
      int nodeCount = s1.size();
      EastNorth[] pts = new EastNorth[nodeCount];
      EastNorth[] normals = new EastNorth[nodeCount - 1];
      int i = 0;
      for (Node n : s1) {
          EastNorth t = n.getEastNorth();
          pts[i] = t;
          i++;
      }
      for (i = 0; i < nodeCount - 1; i++) {
          double dx = pts[i + 1].getX() - pts[i].getX();
          double dy = pts[i + 1].getY() - pts[i].getY();
          double len = Math.sqrt(dx * dx + dy * dy);
          normals[i] = new EastNorth(-dy / len, dx / len);
      }
     
     
      // This is the core algorithm:
      /* 1. Calculate a parallel line, offset by 'd', to each segment in the path
       * 2. Find the intersection of lines belonging to neighboring segments. These become the new node positions
       * 3. Do some special casing for closed paths
       *
       * Simple and probably not even close to optimal performance wise
       */


      EastNorth[] ppts = new EastNorth[nodeCount];

      EastNorth prevA = pts[0].add(normals[0].scale(d));
      EastNorth prevB = pts[1].add(normals[0].scale(d));
      for (i = 1; i < nodeCount - 1; i++) {
          EastNorth a = pts[i].add(normals[i].scale(d));
          EastNorth b = pts[i + 1].add(normals[i].scale(d));
          if (Geometry.segmentsParallel(a, b, prevA, prevB)) {
              ppts[i] = a;
          } else {
              ppts[i] = Geometry.getLineLineIntersection(a, b, prevA, prevB);
          }
          prevA = a;
          prevB = b;
      }
      if (false /*isClosedPath()*/) { // FIXME we don't expect a closed path
          EastNorth a = pts[0].add(normals[0].scale(d));
          EastNorth b = pts[1].add(normals[0].scale(d));
          if (Geometry.segmentsParallel(a, b, prevA, prevB)) {
              ppts[0] = a;
          } else {
              ppts[0] = Geometry.getLineLineIntersection(a, b, prevA, prevB);
          }
          ppts[nodeCount - 1] = ppts[0];
      } else {
          ppts[0] = pts[0].add(normals[0].scale(d));
          ppts[nodeCount - 1] = pts[nodeCount - 1].add(normals[nodeCount - 2].scale(d));
      }

      for (i = 0; i < nodeCount; i++) {
          //Logging.warn("makeparallel: moving s2: " + i + "-" + s2.get(i));
          // s2.get(i).setEastNorth(ppts[i]); // apply the coordinates to the nodes in s2
          cmds.add(new MoveCommand( s2.get(i), s2.get(i).getEastNorth(), ppts[i] )); // same but with undo
      }
    }

    if (!cmds.isEmpty()) {
	    UndoRedoHandler.getInstance().add(new SequenceCommand("Make Parallel: move nodes", cmds));
    } // if
    
  } // end  makeSegmentsParallel()


} // end class MakeParallelAction

//EOF
