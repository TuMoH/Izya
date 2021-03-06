/*
 * Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of LibreTorrent.
 *
 * LibreTorrent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibreTorrent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LibreTorrent.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.videolan.vlc.gui.view;

import org.proninyaroslav.libretorrent.core.BencodeFileItem;
import org.proninyaroslav.libretorrent.core.filetree.BencodeFileTree;
import org.proninyaroslav.libretorrent.core.filetree.FileNode;
import org.proninyaroslav.libretorrent.core.filetree.FileTree;
import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * The static class for create and using BencodeFileTree objects.
 *
 * TODO: need refactor (with TorrentContentFileTreeUtils)
 */

public class BencodeFileTreeUtils {
    public static BencodeFileTree buildFileTree(List<BencodeFileItem> files) {
        BencodeFileTree root = new BencodeFileTree(FileTree.ROOT, 0L, FileNode.Type.DIR);
        BencodeFileTree parentTree = root;
        /* It allows reduce the number of iterations on the paths with equal beginnings */
        String prevPath = "";
        /* Sort reduces the returns number to root */
        Collections.sort(files);

        for (BencodeFileItem file : files) {
            String path;
            /*
             * Compare previous path with new path.
             * Example:
             * prev = dir1/dir2/
             * cur  = dir1/dir2/file1
             *        |________|
             *          equal
             *
             * prev = dir1/dir2/
             * cur  = dir3/file2
             *        |________|
             *         not equal
             */
            if (!prevPath.isEmpty() &&
                    file.getPath().regionMatches(true, 0, prevPath, 0, prevPath.length())) {
                /*
                 * Beginning paths are equal, remove previous path from the new path.
                 * Example:
                 * prev = dir1/dir2/
                 * cur  = dir1/dir2/file1
                 * new  = file1
                 */
                path = file.getPath().substring(prevPath.length());
            } else {
                /* Beginning paths are not equal, return to root */
                path = file.getPath();
                parentTree = root;
            }

            String[] nodes = FileIOUtils.parsePath(path);
            /*
             * Remove last node (file) from previous path.
             * Example:
             * cur = dir1/dir2/file1
             * new = dir1/dir2/
             */
            prevPath = file.getPath()
                    .substring(0, file.getPath().length() - nodes[nodes.length - 1].length());

            /* Iterates path nodes */
            for (int i = 0; i < nodes.length; i++) {
                if (!parentTree.contains(nodes[i])) {
                    /* The last leaf item is a file */
                    parentTree.addChild(
                            makeObject(file.getIndex(), nodes[i],
                                    file.getSize(), parentTree,
                                    i == (nodes.length - 1)));
                }

                BencodeFileTree nextParent = parentTree.getChild(nodes[i]);
                /* Skipping leaf nodes */
                if (!nextParent.isFile()) {
                    parentTree = nextParent;
                }
            }
        }

        return root;
    }

    private static BencodeFileTree makeObject(int index, String name,
                                              long size, BencodeFileTree parent,
                                              boolean isFile) {
        return (isFile ?
                new BencodeFileTree(index, name, size, FileNode.Type.FILE, parent) :
                new BencodeFileTree(name, 0L, FileNode.Type.DIR, parent));
    }

    public static List<BencodeFileTree> getFiles(BencodeFileTree node) {
        return new FileTreeDepthFirstSearch<BencodeFileTree>().getLeaves(node);
    }

    public static Map<Integer, BencodeFileTree> getFilesAsMap(BencodeFileTree node) {
        return new FileTreeDepthFirstSearch<BencodeFileTree>().getLeavesAsMap(node);
    }

    private static class FileTreeDepthFirstSearch<F extends FileTree<F>> {
        private List<F> leaves = new ArrayList<>();
        private Map<Integer, F> leavesAsMap = new HashMap<>();
        private int findIndex = -1;
        private F findResult;

    /*
     * Not recommended for use with a large number of nodes
     * due to deterioration of performance, use getLeaves() method
     */

        public F find(F node, int index) {
            if (node == null) {
                return null;
            }

            if (findIndex == -1) {
                findIndex = index;
            }

            if (node.getIndex() == findIndex) {
                findResult = node;
            } else {
                for (String n : node.getChildrenName()) {
                    if (!node.isFile()) {
                        find(node.getChild(n), index);
                    }
                }
            }

            findIndex = -1;

            return findResult;
        }

    /*
     * Returns the leaf nodes of the tree.
     */

        public List<F> getLeaves(F node) {
            if (node == null) {
                return null;
            }

            if (node.isFile()) {
                leaves.add(node);
            }

            for (F n : node.getChildren()) {
                if (!node.isFile()) {
                    getLeaves(n);
                }
            }

            return leaves;
        }

        public Map<Integer, F> getLeavesAsMap(F node) {
            if (node == null) {
                return null;
            }

            if (node.isFile()) {
                leavesAsMap.put(node.getIndex(), node);
            }

            for (F n : node.getChildren()) {
                if (!node.isFile()) {
                    getLeavesAsMap(n);
                }
            }

            return leavesAsMap;
        }
    }

}
