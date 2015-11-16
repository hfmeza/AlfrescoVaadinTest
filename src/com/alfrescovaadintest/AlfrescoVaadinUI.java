package com.alfrescovaadintest;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.annotation.WebServlet;

import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.Property;
import org.apache.chemistry.opencmis.client.api.Repository;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.api.SessionFactory;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.enums.BindingType;

import com.vaadin.annotations.Theme;
import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.event.ItemClickEvent;
import com.vaadin.server.StreamResource;
import com.vaadin.server.StreamResource.StreamSource;
import com.vaadin.server.ThemeResource;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinServlet;
import com.vaadin.ui.BrowserFrame;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Embedded;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Table;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.TextField;
import com.vaadin.ui.Tree;
import com.vaadin.ui.Tree.ExpandEvent;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;

@SuppressWarnings("serial")
@Theme("AlfrescoVaadin")
public class AlfrescoVaadinUI extends UI {

	private static String URL = "http://127.0.0.1:8080/alfresco/api/-default-/public/cmis/versions/1.1/browser/";
	private static String FOLDER_TYPE_ID = "cmis:folder";
	public static String DOCUMENT_TYPE_ID = "cmis:document";

	private TextField user;
	private TextField password;
	private Session session;
	private Table metadata;

	final ThemeResource folderResource = new ThemeResource("img/folder.png");
	final ThemeResource documentResource = new ThemeResource("img/document.png");
	public VerticalLayout layout;

	@WebServlet(value = "/*", asyncSupported = true)
	@VaadinServletConfiguration(productionMode = false, ui = AlfrescoVaadinUI.class)
	public static class Servlet extends VaadinServlet {
	}

	@Override
	protected void init(VaadinRequest request) {
		layout = new VerticalLayout();
		final HorizontalLayout treeLayout = new HorizontalLayout();

		layout.setMargin(true);
		layout.setSpacing(true);
		setContent(layout);

		setLoginArea(layout);

		metadata = new Table("Metadata");
		metadata.addContainerProperty("Propiedad", String.class, null);
		metadata.addContainerProperty("Valor", TextArea.class, null);

		metadata.setColumnHeader("Propiedad", "Propiedad");
		metadata.setColumnHeader("Valor", "Valor");

		Button button = new Button("Login");
		button.addClickListener(new Button.ClickListener() {
			public void buttonClick(ClickEvent event) {
				loadTree(layout, treeLayout);
			}
		});
		layout.addComponent(button);
	}

	/**
	 * Genera los campos para hacer login
	 * 
	 * @param layout
	 */
	private void setLoginArea(final VerticalLayout layout) {
		user = new TextField("User");
		user.setValue("admin");
		password = new TextField("Password");
		password.setValue("admin");

		layout.addComponent(user);
		layout.addComponent(password);
	}

	/**
	 * Carga el árbol de folders y documentos
	 * 
	 * @param layout
	 * @param treeLayout
	 */
	private void loadTree(final VerticalLayout layout, final HorizontalLayout treeLayout) {
		String userText = user.getValue();
		String passwordText = user.getValue();

		// default factory implementation
		SessionFactory factory = SessionFactoryImpl.newInstance();
		Map<String, String> parameter = new HashMap<String, String>();

		// user credentials
		parameter.put(SessionParameter.USER, userText);
		parameter.put(SessionParameter.PASSWORD, passwordText);

		// connection settings
		parameter.put(SessionParameter.BROWSER_URL, URL);
		parameter.put(SessionParameter.BINDING_TYPE, BindingType.BROWSER.value());

		try {
			List<Repository> repositories = factory.getRepositories(parameter);
			session = repositories.get(0).createSession();
			Notification.show("Login exitoso", Notification.Type.HUMANIZED_MESSAGE);

			Folder root = session.getRootFolder();
			ItemIterable<CmisObject> children = root.getChildren();
			final Tree tree = new Tree("Folders");

			// Genera folders de primer nivel
			for (CmisObject o : children) {
				tree.addItem(o);
				tree.setItemCaption(o, o.getName());
				setIconAndChildren(folderResource, documentResource, tree, o);
			}

			treeLayout.addComponent(tree);
			treeLayout.addComponent(metadata);
			metadata.setWidth("70%");
			tree.setWidth("50%");

			treeLayout.setSizeFull();

			layout.addComponent(treeLayout);

			tree.addExpandListener(new Tree.ExpandListener() {
				@Override
				public void nodeExpand(ExpandEvent event) {
					expandeFolder(tree, event);
				}
			});

			tree.addItemClickListener(new DocumentClickListener(this));

		} catch (org.apache.chemistry.opencmis.commons.exceptions.CmisUnauthorizedException e) {
			Notification.show("Error haciendo login", Notification.Type.ERROR_MESSAGE);
		}
	}

	/**
	 * Para un objeto cmis, determina si es folder o documento y si puede tener
	 * hijos. También asigna su icono.
	 * 
	 * @param folderResource
	 * @param documentResource
	 * @param tree
	 * @param o
	 */
	private void setIconAndChildren(final ThemeResource folderResource, final ThemeResource documentResource,
			final Tree tree, CmisObject o) {
		if (o.getType().getId().equals(DOCUMENT_TYPE_ID)) {
			tree.setChildrenAllowed(o, false);
			tree.setItemIcon(o, documentResource);
		}
		if (o.getType().getId().equals(FOLDER_TYPE_ID)) {
			tree.setItemIcon(o, folderResource);
		}
	}

	/**
	 * Expande un folder del árbol, obteniendo su contenido
	 * 
	 * @param tree
	 * @param event
	 */
	private void expandeFolder(final Tree tree, ExpandEvent event) {
		CmisObject ob = (CmisObject) event.getItemId();
		if (ob.getType().getId().equals(FOLDER_TYPE_ID)) {
			CmisObject object = session.getObject(session.createObjectId(ob.getId()));

			Collection children = tree.getChildren(ob);
			if (children != null) {
				for (Object o : children) {
					tree.removeItem(o);
				}
			}

			Folder folder = (Folder) object;
			ItemIterable<CmisObject> childrenFolder = folder.getChildren();
			for (CmisObject o : childrenFolder) {
				tree.addItem(o);
				tree.setItemCaption(o, o.getName());
				tree.setParent(o, ob);
				setIconAndChildren(folderResource, documentResource, tree, o);
			}
		}
	}

	public void loadMetadataTable(CmisObject ob) {
		List<Property<?>> propertiesList = ob.getProperties();
		metadata.removeAllItems();
		for (Property p : propertiesList) {

			TextArea value = new TextArea();
			value.setSizeFull();
			value.setRows(3);
			value.setWordwrap(true);
			value.setValue(p.getValue() != null ? p.getValue().toString() : "");
			value.setSizeFull();
			value.setReadOnly(true);

			metadata.addItem(new Object[] { p.getDisplayName(), value }, p);
		}
	}

}

class DocumentClickListener implements ItemClickEvent.ItemClickListener {

	private AlfrescoVaadinUI ui;

	DocumentClickListener(AlfrescoVaadinUI ui) {
		this.ui = ui;
	}

	@Override
	public void itemClick(ItemClickEvent event) {
		CmisObject ob = (CmisObject) event.getItemId();
		// System.out.println(ob);
		ui.loadMetadataTable(ob);

		System.out.println(ob.getType().getId());

		if (ob.getType().getId().equals(AlfrescoVaadinUI.DOCUMENT_TYPE_ID)) {
			final Document d = (Document) ob;
			System.out.println(d);
			
			StreamSource s = new StreamResource.StreamSource() {
				@Override
				public InputStream getStream() {
					try {
						System.out.println("leyendo stream");
						return d.getContentStream().getStream();
					} catch (Exception e) {
						e.printStackTrace();
						return null;
					}
				}
			};
			System.out.println("creando embedded");
			StreamResource r = new StreamResource(s, d.getName());
			BrowserFrame e = new BrowserFrame("Documento", r);
			e.setSizeFull();
			e.setHeight("100%");
			r.setMIMEType(d.getContentStreamMimeType());

			//ui.layout.addComponent(e);
			Window content = new Window("Content");
			content.setHeight("650px");
			content.setWidth("650px");
			content.setContent(e);
			content.center();
	        ui.addWindow(content);

		}
	}
}
