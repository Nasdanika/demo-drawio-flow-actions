package org.nasdanika.demo.drawio.actions.flow;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.eclipse.emf.common.util.ECollections;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.nasdanika.common.ConsumerFactory;
import org.nasdanika.common.Context;
import org.nasdanika.common.DefaultConverter;
import org.nasdanika.common.Diagnostic;
import org.nasdanika.common.DiagnosticException;
import org.nasdanika.common.MutableContext;
import org.nasdanika.common.NasdanikaException;
import org.nasdanika.common.NullProgressMonitor;
import org.nasdanika.common.ProgressMonitor;
import org.nasdanika.common.Status;
import org.nasdanika.common.SupplierFactory;
import org.nasdanika.drawio.ConnectionBase;
import org.nasdanika.emf.EObjectAdaptable;
import org.nasdanika.emf.persistence.EObjectLoader;
import org.nasdanika.emf.persistence.GitMarkerFactory;
import org.nasdanika.exec.ExecPackage;
import org.nasdanika.exec.content.ContentFactory;
import org.nasdanika.exec.content.ContentPackage;
import org.nasdanika.exec.resources.Container;
import org.nasdanika.exec.resources.ResourcesFactory;
import org.nasdanika.exec.resources.ResourcesPackage;
import org.nasdanika.html.jstree.JsTreeFactory;
import org.nasdanika.html.jstree.JsTreeNode;
import org.nasdanika.html.model.app.Action;
import org.nasdanika.html.model.app.AppFactory;
import org.nasdanika.html.model.app.AppPackage;
import org.nasdanika.html.model.app.Label;
import org.nasdanika.html.model.app.Link;
import org.nasdanika.html.model.app.drawio.ResourceFactory;
import org.nasdanika.html.model.app.gen.ActionContentProvider;
import org.nasdanika.html.model.app.gen.AppAdapterFactory;
import org.nasdanika.html.model.app.gen.AppGenObjectLoaderSupplier;
import org.nasdanika.html.model.app.gen.LinkJsTreeNodeSupplierFactoryAdapter;
import org.nasdanika.html.model.app.gen.NavigationPanelConsumerFactoryAdapter;
import org.nasdanika.html.model.app.gen.PageContentProvider;
import org.nasdanika.html.model.app.gen.Util;
import org.nasdanika.html.model.bootstrap.BootstrapPackage;
import org.nasdanika.html.model.html.HtmlPackage;
import org.nasdanika.ncore.NcorePackage;
import org.nasdanika.ncore.util.NcoreResourceSet;
import org.nasdanika.persistence.ObjectLoader;
import org.nasdanika.persistence.ObjectLoaderResourceFactory;
import org.nasdanika.resources.BinaryEntityContainer;
import org.nasdanika.resources.FileSystemContainer;

import com.redfin.sitemapgenerator.ChangeFreq;
import com.redfin.sitemapgenerator.WebSitemapGenerator;
import com.redfin.sitemapgenerator.WebSitemapUrl;

/**
 * Tests of agile flows.
 * @author Pavel
 *
 */
public class DrawioFlowActionsGenerator {
	
	private static final File GENERATED_MODELS_BASE_DIR = new File("target/model-doc");
	private static final File RESOURCE_MODELS_DIR = new File(GENERATED_MODELS_BASE_DIR, "resources");
	
	/**
	 * Generates a resource model from an action model.
	 * @throws Exception
	 */
	private void generateResourceModel(String name, Context context, ProgressMonitor progressMonitor) throws Exception {
		ResourceSet resourceSet = createResourceSet(context, progressMonitor);
		
		EObjectLoader eObjectLoader = new EObjectLoader(null, null, resourceSet);
		eObjectLoader.setMarkerFactory(new GitMarkerFactory());
		
		Map<String, Object> extensionToFactoryMap = resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap();
		extensionToFactoryMap.put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
		
		ObjectLoaderResourceFactory objectLoaderResourceFactory = new ObjectLoaderResourceFactory() {
			
			@Override
			protected ObjectLoader getObjectLoader(Resource resource) {
				return eObjectLoader;
			}
			
			@Override
			protected Context getContext(Resource resource) {
				return context;
			}
			
			@Override
			protected ProgressMonitor getProgressMonitor(Resource resource) {
				return progressMonitor;
			}
			
		};
		
		extensionToFactoryMap.put("yml", objectLoaderResourceFactory);
		extensionToFactoryMap.put("json", objectLoaderResourceFactory);		
		resourceSet.getResourceFactoryRegistry().getProtocolToFactoryMap().put("data", objectLoaderResourceFactory);
		
		Consumer<Diagnostic> diagnosticConsumer = diagnostic -> {
			if (diagnostic.getStatus() != Status.SUCCESS) {
				throw new DiagnosticException(diagnostic);
			}
		};
		
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("drawio", new ResourceFactory(ConnectionBase.SOURCE, resourceSet) {
						
			@Override
			protected Action createDocumentAction(org.nasdanika.drawio.Document document) {
				String actionsResource = "model/root-action.yml";
				Action root = (Action) Objects.requireNonNull(loadObject(actionsResource, diagnosticConsumer, context, progressMonitor), "Loaded null from " + actionsResource);
				
				return EcoreUtil.copy(root);
			}
						
			@Override
			protected ProgressMonitor getProgressMonitor(URI uri) {
				return progressMonitor.split("Loading " + uri, 1);
			}
			
		});
		
		File modelFile = new File("model/" + name);
		if (!modelFile.isFile()) {
			throw new IllegalArgumentException("Not a file: " + modelFile);
		}
		Resource modelResource = resourceSet.getResource(URI.createFileURI(modelFile.getCanonicalPath()), true);
		
		Action root = (Action) modelResource.getContents().get(0);
		
		String searchActionResource = "model/search-action.yml";
		Action searchAction = (Action) Objects.requireNonNull(loadObject(searchActionResource, diagnosticConsumer, context, progressMonitor), "Loaded null from " + searchActionResource);
		root.getChildren().add(searchAction);
		
		Container container = ResourcesFactory.eINSTANCE.createContainer();
		container.setName(name);
		
		ResourceSet containerResourceSet = createResourceSet(context, progressMonitor);
		containerResourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
		URI containerModelUri = URI.createFileURI(new File(RESOURCE_MODELS_DIR, name + ".xml").getAbsolutePath());				
		Resource containerResource = containerResourceSet.createResource(containerModelUri);
		containerResource.getContents().add(container);
		
		String pageTemplateResource = "model/page-template.yml";
		org.nasdanika.html.model.bootstrap.Page pageTemplate = (org.nasdanika.html.model.bootstrap.Page) Objects.requireNonNull(loadObject(pageTemplateResource, diagnosticConsumer, context, progressMonitor), "Loaded null from " + pageTemplateResource);
		
		containerResource.save(null);		
		File contentDir = new File(RESOURCE_MODELS_DIR, "content");
		contentDir.mkdirs();
		
		// Generating content file from action content 
		ActionContentProvider.Factory actionContentProviderFactory = (contentProviderContext) -> (action, uriResolver, pMonitor) -> {
			
			@SuppressWarnings("unchecked")
			java.util.function.Function<Context, String> siteMapTreeScriptComputer = ctx -> {
				// TODO - actions from action prototype, e.g. Ecore doc actions, to the tree.
				
				JsTreeFactory jsTreeFactory = context.get(JsTreeFactory.class, JsTreeFactory.INSTANCE);
				Map<Action, JsTreeNode> nodeMap = new HashMap<>();
				
				Action firstPage = (Action) root.getChildren().get(0);
				nodeMap.put(firstPage, createActionSearchJsTreeNode(firstPage, action, uriResolver, ctx, progressMonitor));
				TreeIterator<EObject> cit = firstPage.eAllContents();
				while (cit.hasNext()) {
					EObject next = cit.next();
					if (next instanceof Action) {
						if (next.eContainmentFeature() != AppPackage.Literals.ACTION__SECTIONS) {
							nodeMap.put((Action) next, createActionSearchJsTreeNode((Action) next, action, uriResolver, ctx, progressMonitor));
						}
					}
				}
				
				Map<EObject, JsTreeNode> roots = new HashMap<>(nodeMap);
				
				Map<Action,Map<String,List<JsTreeNode>>> refMap = new HashMap<>();
				for (Action mappedAction: new ArrayList<>(nodeMap.keySet())) {
					Map<String,List<JsTreeNode>> rMap = new TreeMap<>();					
					for (EReference eRef: mappedAction.eClass().getEAllReferences()) {
						if (eRef.isContainment()) {
							Object eRefValue = mappedAction.eGet(eRef);
							List<JsTreeNode> refNodes = new ArrayList<>();
							for (Object ve: eRefValue instanceof Collection ? (Collection<Object>) eRefValue : Collections.singletonList(eRefValue)) {
								JsTreeNode refNode = roots.remove(ve);
								if (refNode != null) {
									refNodes.add(refNode);
								}
							}
							if (!refNodes.isEmpty()) {
								rMap.put(org.nasdanika.common.Util.nameToLabel(eRef.getName()) , refNodes);
							}
						}
					}
					if (!rMap.isEmpty()) {
						refMap.put(mappedAction, rMap);
					}
				}
				
				for (Entry<Action, JsTreeNode> ne: nodeMap.entrySet()) {
					Map<String, List<JsTreeNode>> refs = refMap.get(ne.getKey());
					if (refs != null) {
						for (Entry<String, List<JsTreeNode>> ref: refs.entrySet()) {
							JsTreeNode refNode = jsTreeFactory.jsTreeNode();
							refNode.text(ref.getKey());
							refNode.children().addAll(ref.getValue());
							ne.getValue().children().add(refNode);
						}
					}
				}
				
				JSONObject jsTree = jsTreeFactory.buildJsTree(roots.values());
		
				List<String> plugins = new ArrayList<>();
				plugins.add("state");
				plugins.add("search");
				JSONObject searchConfig = new JSONObject();
				searchConfig.put("show_only_matches", true);
				jsTree.put("search", searchConfig);
				jsTree.put("plugins", plugins); 		
				jsTree.put("state", Collections.singletonMap("key", "nsd-site-map-tree"));
				
				// Deletes selection from state
				String filter = NavigationPanelConsumerFactoryAdapter.CLEAR_STATE_FILTER + " tree.search.search_callback = (results, node) => results.split(' ').includes(node.original['data-nsd-action-uuid']);";
				
				return jsTreeFactory.bind("#nsd-site-map-tree", jsTree, filter, null).toString();				
			};			
			MutableContext mctx = contentProviderContext.fork();
			mctx.put("nsd-site-map-tree-script", siteMapTreeScriptComputer);		
			
			String fileName = action.getUuid() + ".html";
			SupplierFactory<InputStream> contentFactory = org.nasdanika.common.Util.asInputStreamSupplierFactory(action.getContent());			
			try (InputStream contentStream = org.nasdanika.common.Util.call(contentFactory.create(mctx), pMonitor, diagnosticConsumer, Status.FAIL, Status.ERROR)) {
				Files.copy(contentStream, new File(contentDir, fileName).toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new NasdanikaException(e);
			}
			
			org.nasdanika.exec.content.Resource contentResource = ContentFactory.eINSTANCE.createResource();
			contentResource.setLocation("../content/" + fileName);
			System.out.println("[Action content] " + action.getName() + " -> " + fileName);
			return ECollections.singletonEList(contentResource);			
		};
		
		File pagesDir = new File(RESOURCE_MODELS_DIR, "pages");
		pagesDir.mkdirs();
		PageContentProvider.Factory pageContentProviderFactory = (contentProviderContext) -> (page, baseURI, uriResolver, pMonitor) -> {
			try {
				// Saving a page to .xml and creating a reference to .html; Pages shall be processed from .xml to .html individually.
				page.setUuid(UUID.randomUUID().toString());
				
				ResourceSet pageResourceSet = new ResourceSetImpl();
				pageResourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());			
				URI pageURI = URI.createFileURI(new File(pagesDir, page.getUuid() + ".xml").getCanonicalPath());
				Resource pageEResource = pageResourceSet.createResource(pageURI);
				pageEResource.getContents().add(page);
				pageEResource.save(null);
				
				org.nasdanika.exec.content.Resource pageResource = ContentFactory.eINSTANCE.createResource();
				pageResource.setLocation("pages/" + page.getUuid() + ".html");
				System.out.println("[Page content] " + page.getName() + " -> " + pageResource.getLocation());
				return pageResource;
			} catch (IOException e) {
				throw new NasdanikaException(e);
			}
		};
		
		Util.generateSite(
				root, 
				pageTemplate,
				container,
				actionContentProviderFactory,
				pageContentProviderFactory,
				context,
				progressMonitor);
		
		containerResource.save(null);
		
		// Page model to XML conversion
		ResourceSet pageResourceSet = new ResourceSetImpl();
		pageResourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());			
		pageResourceSet.getAdapterFactories().add(new AppAdapterFactory());
		for (File pageFile: pagesDir.listFiles(f -> f.getName().endsWith(".xml"))) {
			URI pageURI = URI.createFileURI(pageFile.getCanonicalPath());
			Resource pageEResource = pageResourceSet.getResource(pageURI, true);
			SupplierFactory<InputStream> contentFactory = org.nasdanika.common.Util.asInputStreamSupplierFactory(pageEResource.getContents());			
			try (InputStream contentStream = org.nasdanika.common.Util.call(contentFactory.create(context), progressMonitor, diagnosticConsumer, Status.FAIL, Status.ERROR)) {
				Files.copy(contentStream, new File(pageFile.getCanonicalPath().replace(".xml", ".html")).toPath(), StandardCopyOption.REPLACE_EXISTING);
				System.out.println("[Page xml -> html] " + pageFile.getName());
			}
		}				
	}

	protected JsTreeNode createActionSearchJsTreeNode(
			Action treeAction, 
			Action action,
			BiFunction<Label, URI, URI> uriResolver, 
			Context context,
			ProgressMonitor progressMonitor) {
		
		Link link = AppFactory.eINSTANCE.createLink();
		String treeActionText = treeAction.getText();
		int maxLength = 50;
		link.setText(treeActionText.length() > maxLength ? treeActionText.substring(0, maxLength) + "..." : treeActionText);
		link.setIcon(treeAction.getIcon());
		
		URI bURI = uriResolver.apply(action, (URI) null);
		URI tURI = uriResolver.apply(treeAction, bURI);
//		if (tURI == null) {
//			return null; // Sections
//		}
		link.setLocation(tURI.toString());
		LinkJsTreeNodeSupplierFactoryAdapter<Link> adapter = new LinkJsTreeNodeSupplierFactoryAdapter<>(link);
		
		try {
			JsTreeNode jsTreeNode = adapter.create(context).execute(progressMonitor);
			jsTreeNode.attribute(Util.DATA_NSD_ACTION_UUID_ATTRIBUTE, treeAction.getUuid());
			return jsTreeNode;
		} catch (Exception e) {
			throw new NasdanikaException(e);
		}
	}
	
	private static EObject loadObject(
			String resource, 
			java.util.function.Consumer<org.nasdanika.common.Diagnostic> diagnosticConsumer,
			Context context,
			ProgressMonitor progressMonitor) {
		
		URI resourceURI = URI.createFileURI(new File(resource).getAbsolutePath());

		// Diagnosing loaded resources. 
		try {
			return Objects.requireNonNull(org.nasdanika.common.Util.call(new AppGenObjectLoaderSupplier(resourceURI, context), progressMonitor, diagnosticConsumer), "Loaded null from: " + resource);
		} catch (DiagnosticException e) {
			System.err.println("******************************");
			System.err.println("*      Diagnostic failed     *");
			System.err.println("******************************");
			e.getDiagnostic().dump(System.err, 4, Status.FAIL);
			throw e;
		}		
	}
	
	private static ResourceSet createResourceSet(Context context, ProgressMonitor progressMonitor) {
		// Load model from XMI
		ResourceSet resourceSet = new NcoreResourceSet();
		
		EObjectLoader eObjectLoader = new EObjectLoader(null, null, resourceSet);
		GitMarkerFactory markerFactory = new GitMarkerFactory();
		eObjectLoader.setMarkerFactory(markerFactory);
		
		Resource.Factory objectLoaderResourceFactory = new ObjectLoaderResourceFactory() {
			
			@Override
			protected org.nasdanika.persistence.ObjectLoader getObjectLoader(Resource resource) {
				return eObjectLoader;
			}
			
		};
		Map<String, Object> extensionToFactoryMap = resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap();
		extensionToFactoryMap.put("yml", objectLoaderResourceFactory);
		extensionToFactoryMap.put("json", objectLoaderResourceFactory);
		resourceSet.getResourceFactoryRegistry().getProtocolToFactoryMap().put("data", objectLoaderResourceFactory);
		
		extensionToFactoryMap.put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
	
		resourceSet.getPackageRegistry().put(NcorePackage.eNS_URI, NcorePackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(ExecPackage.eNS_URI, ExecPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(ContentPackage.eNS_URI, ContentPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(ResourcesPackage.eNS_URI, ResourcesPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(HtmlPackage.eNS_URI, HtmlPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(BootstrapPackage.eNS_URI, BootstrapPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(AppPackage.eNS_URI, AppPackage.eINSTANCE);
		
		resourceSet.getAdapterFactories().add(new AppAdapterFactory());
		
		return resourceSet;
	}
		
	/**
	 * Generates files from the previously generated resource model.
	 * @throws Exception
	 */
	public void generateContainer(String name, Context context, ProgressMonitor progressMonitor) throws Exception {
		ResourceSet resourceSet = createResourceSet(context, progressMonitor);
		
		resourceSet.getAdapterFactories().add(new AppAdapterFactory());
				
		URI containerModelUri = URI.createFileURI(new File(RESOURCE_MODELS_DIR, name + ".xml").getAbsolutePath());				
		Resource containerResource = resourceSet.getResource(containerModelUri, true);
	
		File siteDir = new File(GENERATED_MODELS_BASE_DIR, "site");
		BinaryEntityContainer container = new FileSystemContainer(siteDir);
		for (EObject eObject : containerResource.getContents()) {
			Diagnostician diagnostician = new Diagnostician();
			org.eclipse.emf.common.util.Diagnostic diagnostic = diagnostician.validate(eObject);
			if (org.eclipse.emf.common.util.Diagnostic.ERROR == diagnostic.getSeverity()) {
				throw new org.eclipse.emf.common.util.DiagnosticException(diagnostic);
			}
			// Diagnosing loaded resources. 
			try {
				ConsumerFactory<BinaryEntityContainer> consumerFactory = Objects.requireNonNull(EObjectAdaptable.adaptToConsumerFactory(eObject, BinaryEntityContainer.class), "Cannot adapt to ConsumerFactory");
				Diagnostic callDiagnostic = org.nasdanika.common.Util.call(consumerFactory.create(context), container, progressMonitor);
				if (callDiagnostic.getStatus() == Status.FAIL || callDiagnostic.getStatus() == Status.ERROR) {
					System.err.println("***********************");
					System.err.println("*      Diagnostic     *");
					System.err.println("***********************");
					callDiagnostic.dump(System.err, 4, Status.FAIL, Status.ERROR);
				}
				if (Status.SUCCESS != callDiagnostic.getStatus()) {
					throw new DiagnosticException(callDiagnostic);
				}
			} catch (DiagnosticException e) {
				System.err.println("******************************");
				System.err.println("*      Diagnostic failed     *");
				System.err.println("******************************");
				e.getDiagnostic().dump(System.err, 4, Status.FAIL);
				throw e;
			}
		}
		
		// Cleanup docs, keep CNAME, favicon.ico, and images directory. Copy from target/model-doc/site/docs
		Predicate<String> cleanPredicate = path -> {
			return !"CNAME".equals(path) && !"favicon.ico".equals(path) && !path.startsWith("images/");
		};

		File docsDir = new File("docs");
		copy(new File(siteDir, name + "\\flow"), docsDir, true, cleanPredicate, null);
		
		generateSitemapAndSearch(docsDir);
	}
	
	private static void delete(String path, Predicate<String> deletePredicate, File... files) {
		for (File file: files) {
			String filePath = path == null ? file.getName() : path + "/" + file.getName();
			if (file.exists() && (deletePredicate == null || deletePredicate.test(filePath))) {
				if (file.isDirectory()) {
					delete(filePath, deletePredicate, file.listFiles());
				}
				file.delete();
			}
		}
	}	
		
	private static void copy(File source, File target, boolean cleanTarget, Predicate<String> cleanPredicate, BiConsumer<File,File> listener) throws IOException {
		if (cleanTarget && target.isDirectory()) {
			delete(null, cleanPredicate, target.listFiles());
		}
		if (source.isDirectory()) {
			target.mkdirs();
			for (File sc: source.listFiles()) {
				copy(sc, new File(target, sc.getName()), false, listener);
			}
		} else if (source.isFile()) {
			Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);			
			if (listener != null) {
				listener.accept(source, target);
			}
		}
	}
	
	private static void copy(File source, File target, boolean cleanTarget, BiConsumer<File,File> listener) throws IOException {
		if (cleanTarget && target.isDirectory()) {
			delete(target.listFiles());
		}
		if (source.isDirectory()) {
			target.mkdirs();
			for (File sc: source.listFiles()) {
				copy(sc, new File(target, sc.getName()), false, listener);
			}
		} else if (source.isFile()) {
			Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);			
			if (listener != null) {
				listener.accept(source, target);
			}
		}
	}

	/**
	 * 
	 * @param siteDir
	 * @return Number of broken links.
	 * @throws IOException
	 */
	private void generateSitemapAndSearch(File siteDir) throws IOException {
		AtomicInteger problems = new AtomicInteger();
		
		// Site map and search index
		JSONObject searchDocuments = new JSONObject();		
		String baseURL = "https://docs.nasdanika.org/demo-drawio-actions";
		WebSitemapGenerator wsg = new WebSitemapGenerator(baseURL, siteDir);
		BiConsumer<File, String> listener = new BiConsumer<File, String>() {
			
			@Override
			public void accept(File file, String path) {
				if (path.endsWith(".html")) {
					try {
						WebSitemapUrl url = new WebSitemapUrl.Options(baseURL + "/" + path)
							    .lastMod(new Date(file.lastModified())).changeFreq(ChangeFreq.WEEKLY).build();
						wsg.addUrl(url); 
					} catch (MalformedURLException e) {
						throw new NasdanikaException(e);
					}
					
					// Excluding search.html and aggregator pages which contain information present elsewhere
					try {	
						Predicate<String> predicate = org.nasdanika.html.model.app.gen.Util.createRelativeLinkPredicate(file, siteDir);						
						Consumer<? super Element> inspector = org.nasdanika.html.model.app.gen.Util.createInspector(predicate, error -> {
							System.err.println("[" + path +"] " + error);
							problems.incrementAndGet();
						});
						JSONObject searchDocument = org.nasdanika.html.model.app.gen.Util.createSearchDocument(path, file, inspector, DrawioFlowActionsGenerator.this::configureSearch);
						if (searchDocument != null) {
							searchDocuments.put(path, searchDocument);
						}
					} catch (IOException e) {
						throw new NasdanikaException(e);
					}
				}
			}
		};
		org.nasdanika.common.Util.walk(null, listener, siteDir.listFiles());
		wsg.write();	

		try (FileWriter writer = new FileWriter(new File(siteDir, "search-documents.js"))) {
			writer.write("var searchDocuments = " + searchDocuments);
		}
		
		if (problems.get() > 0) {
			throw new NasdanikaException("There are broken links: " + problems.get());
		};
	}
		
	protected boolean configureSearch(String path, Document doc) {
		Element head = doc.head();
		URI base = URI.createURI("temp://" + UUID.randomUUID() + "/");
		URI searchScriptURI = URI.createURI("search-documents.js").resolve(base);
		URI thisURI = URI.createURI(path).resolve(base);
		URI relativeSearchScriptURI = searchScriptURI.deresolve(thisURI, true, true, true);
		head.append(System.lineSeparator() + "<script src=\"" + relativeSearchScriptURI + "\"></script>" + System.lineSeparator());
		head.append(System.lineSeparator() + "<script src=\"https://unpkg.com/lunr/lunr.js\"></script>" + System.lineSeparator());
				
		try (InputStream in = new FileInputStream("model/search.js")) {
			head.append(System.lineSeparator() + "<script>" + System.lineSeparator() + DefaultConverter.INSTANCE.toString(in) + System.lineSeparator() + "</script>" + System.lineSeparator());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return true;
	}	
		
	private static void delete(File... files) {
		for (File file: files) {
			if (file.exists()) {
				if (file.isDirectory()) {
					delete(file.listFiles());
				}
				file.delete();
			}
		}
	}	
	
	public void generate() throws Exception {
		delete(new File("docs").listFiles());
		ProgressMonitor progressMonitor = new NullProgressMonitor(); // PrintStreamProgressMonitor();		
		MutableContext context = Context.EMPTY_CONTEXT.fork();
		
		long start = System.currentTimeMillis();
		String name = "flow.drawio";
		generateResourceModel(name, context, progressMonitor);
		long grm = System.currentTimeMillis();
		generateContainer(name, context, progressMonitor);
		long end = System.currentTimeMillis();
		System.out.println((grm - start) + "/" + (end - grm));	
	}
	
}
