// PathVisio WP Client
// Plugin that provides a WikiPathways client for PathVisio.
// Copyright 2013 developed for Google Summer of Code
//
// Licensed under the Apache License, Version 2.0 (the "License"); 
// you may not use this file except in compliance with the License. 
// You may obtain a copy of the License at 
// 
// http://www.apache.org/licenses/LICENSE-2.0 
//  
// Unless required by applicable law or agreed to in writing, software 
// distributed under the License is distributed on an "AS IS" BASIS, 
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
// See the License for the specific language governing permissions and 
// limitations under the License.
//
package org.pathvisio.wpclient.panels;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import javax.xml.rpc.ServiceException;

import org.pathvisio.core.debug.Logger;
import org.pathvisio.core.util.ProgressKeeper;
import org.pathvisio.gui.ProgressDialog;
import org.pathvisio.wikipathways.webservice.WSPathwayInfo;
import org.pathvisio.wikipathways.webservice.WSSearchResult;
import org.pathvisio.wpclient.WikiPathwaysClientPlugin;
import org.pathvisio.wpclient.models.ResultTableModel;
import org.pathvisio.wpclient.validators.Validator;
import org.wikipathways.client.WikiPathwaysClient;

import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

/**
 * This class creates the content in the Dialog of the PathwaySearchPanel
 * TabbedPane of Search
 * 
 * @author Sravanthi Sinha
 * @author Martina Kutmon
 * @version 1.0
 */
public class PathwaySearchPanel extends JPanel {
	WikiPathwaysClientPlugin plugin;
	JTextField pTitleOrId;
	JComboBox clientDropdown;
	java.util.HashMap<String, String> curationtags = new HashMap<String, String>();
	JTable resultTable;
	int i = 0;
	private JScrollPane resultspane;

	public int flag = 0;
	private JLabel tipLabel;
	private JLabel lblNumFound;

	public PathwaySearchPanel(final WikiPathwaysClientPlugin plugin) {

		this.plugin = plugin;

		setLayout(new BorderLayout());
		pTitleOrId = new JTextField();

		Action searchAction = new AbstractAction("Search") {
			public void actionPerformed(ActionEvent e) {
				try {
					resultspane.setBorder(BorderFactory.createTitledBorder(
							BorderFactory.createEtchedBorder(), "Pathways"));
					if (pTitleOrId.getText().startsWith("WP")) {
						searchByID();
					} else {
						searchByTitle();
					}
				} catch (Exception ex) {
					JOptionPane
							.showMessageDialog(PathwaySearchPanel.this,
									ex.getMessage(), "Error",
									JOptionPane.ERROR_MESSAGE);
					Logger.log.error("Error searching WikiPathways", ex);
				}
			}

		};

		pTitleOrId.addActionListener(searchAction);
		tipLabel = new JLabel(
				"Tip: use Pathway Title or Id (e.g.:'Selenium Pathway','WP15')");
		tipLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));

		JPanel searchBox = new JPanel();
		FormLayout layoutf = new FormLayout(
				"p,3dlu,120px,2dlu,30px,pref:grow,3dlu",
				"pref, pref, 4dlu, pref, 4dlu, pref");
		CellConstraints ccf = new CellConstraints();

		searchBox.setLayout(layoutf);
		searchBox.setBorder(BorderFactory.createTitledBorder(BorderFactory
				.createEtchedBorder()));

		JPanel searchOptBox = new JPanel();
		FormLayout layout = new FormLayout(
				"3dlu,p,3dlu,2dlu,30px,fill:pref:grow,2dlu",
				"pref, pref, 4dlu, pref, 4dlu, pref");
		CellConstraints cc = new CellConstraints();

		searchOptBox.setLayout(layout);
		searchOptBox.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createEtchedBorder(), "Search options"));
		searchOptBox.add(new JLabel("Pathway Title/ID"), cc.xy(2, 1));
		searchOptBox.add(pTitleOrId, cc.xyw(4, 1, 3));

		searchOptBox.add(tipLabel, cc.xyw(2, 2, 5));

		searchBox.add(searchOptBox, ccf.xyw(1, 1, 6));

		add(searchBox, BorderLayout.NORTH);

		// Center contains table model for results
		resultTable = new JTable();
		resultspane = new JScrollPane(resultTable);

		add(resultspane, BorderLayout.CENTER);
		lblNumFound = new JLabel();
		add (lblNumFound, BorderLayout.SOUTH);
		pTitleOrId.requestDefaultFocus();

		resultTable.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					JTable target = (JTable) e.getSource();
					int row = target.getSelectedRow();

					try {
						if (flag == 1) {
							SearchTableModel model = (SearchTableModel) target
									.getModel();
							File tmpDir = new File(plugin.getTmpDir(),
									WikiPathwaysClientPlugin
											.shortClientName(model.clientName));
							tmpDir.mkdirs();
							flag = 0;
							plugin.openPathwayWithProgress(
									WikiPathwaysClientPlugin.loadClient(),
									model.getValueAt(row, 0).toString(), 0,
									tmpDir);
						} else {
							ResultTableModel model = (ResultTableModel) target
									.getModel();
							File tmpDir = new File(plugin.getTmpDir(),
									WikiPathwaysClientPlugin
											.shortClientName(model.clientName));
							tmpDir.mkdirs();

							plugin.openPathwayWithProgress(
									WikiPathwaysClientPlugin.loadClient(),
									model.getValueAt(row, 0).toString(), 0,
									tmpDir);

						}
					} catch (Exception ex) {
						JOptionPane.showMessageDialog(PathwaySearchPanel.this,
								ex.getMessage(), "Error",
								JOptionPane.ERROR_MESSAGE);
						Logger.log.error("Error", ex);
					}
				}
			}
		});
	}

	private void searchByTitle() throws RemoteException, InterruptedException,
			ExecutionException, MalformedURLException, ServiceException {
		lblNumFound.setText("");
		final String query = pTitleOrId.getText();
		if (!query.isEmpty()) {
			if (Validator.CheckNonAlpha(query)) {
				final WikiPathwaysClient client = WikiPathwaysClientPlugin
						.loadClient();

				final ProgressKeeper pk = new ProgressKeeper();
				final ProgressDialog d = new ProgressDialog(
						JOptionPane.getFrameForComponent(this), "", pk, true,
						true);
				i = 0;
				SwingWorker<WSSearchResult[], Void> sw = new SwingWorker<WSSearchResult[], Void>() {
					WSSearchResult[] results;

					protected WSSearchResult[] doInBackground()
							throws Exception {
						pk.setTaskName("Searching");

						ArrayList<WSSearchResult> results2 = new ArrayList<WSSearchResult>();
						try {
							pk.setTaskName("Searching By Pathway Title");
							results = client.findPathwaysByText(query);
						} catch (Exception e) {
							throw e;
						} finally {
							pk.finished();
						}

						for (WSSearchResult wsSearchResult : results) {
							if (wsSearchResult.getName().toUpperCase()
									.indexOf(query.toUpperCase()) != -1) {
								results2.add(wsSearchResult);
								i++;

							}
						}
						results = new WSSearchResult[i];
						results2.toArray(results);
						return results;

					}

					protected void done() {
						if (!pk.isCancelled()) {
							if (results.length == 0) {
								JOptionPane.showMessageDialog(null,
										"0 results found");
							}
						} else if (pk.isCancelled()) {
							pk.finished();
						}
					}
				};

				sw.execute();
				d.setVisible(true);
				resultTable.setModel(new ResultTableModel(sw.get(), client
						.toString()));
				resultTable.setRowSorter(new TableRowSorter(resultTable
						.getModel()));
				lblNumFound.setText(" No.of results found: "+sw.get().length);
			} else {
				JOptionPane.showMessageDialog(null,
						"Please Enter a Valid Title", "ERROR",
						JOptionPane.ERROR_MESSAGE);
			}
		} else {
			JOptionPane.showMessageDialog(null, "Please Enter a Search Query",
					"ERROR", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void searchByID() throws RemoteException, InterruptedException,
			ExecutionException, MalformedURLException, ServiceException {
		lblNumFound.setText("");
		final String query = pTitleOrId.getText();

		if (!query.isEmpty()) {
			if (Validator.CheckNonAlpha(query)) {
			final WikiPathwaysClient client = WikiPathwaysClientPlugin
					.loadClient();
			final ProgressKeeper pk = new ProgressKeeper();
			final ProgressDialog d = new ProgressDialog(
					JOptionPane.getFrameForComponent(this), "", pk, true, true);

			SwingWorker<WSPathwayInfo[], Void> sw = new SwingWorker<WSPathwayInfo[], Void>() {
				WSPathwayInfo[] results3;

				protected WSPathwayInfo[] doInBackground() throws Exception {
					i = 0;
					pk.setTaskName("Searching");

					ArrayList<WSPathwayInfo> results2 = new ArrayList<WSPathwayInfo>();
					try {
						pk.setTaskName("Searching By Pathway ID");
						results2.add(client.getPathwayInfo(query));
						i++;
					} catch (Exception e) {
						throw e;
					} finally {
						pk.finished();
					}

					results3 = new WSPathwayInfo[i];
					results2.toArray(results3);
					return results3;

				}

				protected void done() {
					if (!pk.isCancelled()) {
						if (results3.length == 0) {
							JOptionPane.showMessageDialog(null,
									"0 results found");
						}
					} else if (pk.isCancelled()) {
						pk.finished();
					}
				}
			};

			sw.execute();
			d.setVisible(true);
			flag = 1;
			resultTable.setModel(new SearchTableModel(sw.get(), client
					.toString()));
			resultTable
					.setRowSorter(new TableRowSorter(resultTable.getModel()));
			lblNumFound.setText(" No.of results found: "+sw.get().length);
			} else {
				JOptionPane.showMessageDialog(null,
						"Please Enter a Valid ID", "ERROR",
						JOptionPane.ERROR_MESSAGE);
			}
		} else {
			JOptionPane.showMessageDialog(null, "Please Enter a Search Query",
					"ERROR", JOptionPane.ERROR_MESSAGE);
		}
	}

	private class SearchTableModel extends AbstractTableModel {
		WSPathwayInfo[] results;
		String[] columnNames = new String[] { "ID", "Name", "Species" };
		String clientName;

		public SearchTableModel(WSPathwayInfo[] wsPathwayInfos,
				String clientName) {
			this.clientName = clientName;
			this.results = wsPathwayInfos;

		}

		public int getColumnCount() {
			return 3;
		}

		public int getRowCount() {
			return results.length;
		}

		public Object getValueAt(int rowIndex, int columnIndex) {
			WSPathwayInfo r = results[rowIndex];
			switch (columnIndex) {
			case 0:
				return r.getId();
			case 1:
				return r.getName();
			case 2:
				return r.getSpecies();
			}
			return "";

		}

		public String getColumnName(int column) {
			return columnNames[column];
		}

	}

}
