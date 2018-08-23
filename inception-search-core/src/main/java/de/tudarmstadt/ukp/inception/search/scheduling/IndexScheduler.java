/*
 * Copyright 2017
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.search.scheduling;

import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.uima.jcas.JCas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.inception.search.scheduling.tasks.IndexDocumentTask;
import de.tudarmstadt.ukp.inception.search.scheduling.tasks.ReindexTask;
import de.tudarmstadt.ukp.inception.search.scheduling.tasks.Task;

/**
 * Indexer scheduler. Does the project reindexing in an asynchronous way.
 */
@Component
public class IndexScheduler
{
    private Logger log = LoggerFactory.getLogger(getClass());

    private @Autowired ApplicationContext applicationContext;

    private Thread consumer;
    private BlockingQueue<Task> queue = new ArrayBlockingQueue<Task>(100);

    @PostConstruct
    private void startSchedulerThread()
    {
        consumer = new Thread(new TaskConsumer(applicationContext, queue), "Index task consumer");
        consumer.setPriority(Thread.MIN_PRIORITY);
        consumer.start();
        log.info("Started Search Indexing Thread");
    }

    @PreDestroy
    public void destroy()
    {
        consumer.interrupt();
    }

    public void enqueueReindexTask(Project aProject)
    {
        // Add reindex task
        enqueue(new ReindexTask(aProject));
    }

    public void enqueueIndexDocument(SourceDocument aSourceDocument, JCas aJCas)
    {
        // Index source document
        enqueue(new IndexDocumentTask(aSourceDocument, aJCas));
    }

    public void enqueueIndexDocument(AnnotationDocument aAnnotationDocument, JCas aJCas)
    {
        // Index annotation document
        enqueue(new IndexDocumentTask(aAnnotationDocument, aJCas));
    }
    
    public synchronized void enqueue(Task aRunnable)
    {
        if (aRunnable.getAnnotationDocument() == null && aRunnable.getSourceDocument() == null) {
            // Project indexing
            // If there is no indexing in the queue on for this project, enqueue it
            if (!isIndexing(aRunnable.getProject())) {
                queue.offer(aRunnable);
                log.info("Enqueued new indexing task: {}", aRunnable);
            }
        }
        else if (aRunnable.getSourceDocument() != null) {
            // Source document indexing
            // If there is no indexing in the queue on for this project, enqueue it
            if (!isIndexingDocument(aRunnable.getSourceDocument())) {
                queue.offer(aRunnable);
                log.info("Enqueued new source document indexing task: {}", aRunnable);
            }
            else {
                log.debug("No source document indexing task enqueued due to a previous "
                        + "enqueued task: {}", aRunnable);
            }
        }
        else if (aRunnable.getAnnotationDocument() != null) {
            // Annotation document indexing
            // If there is no indexing in the queue on for this project, enqueue it
            if (!isIndexingDocument(aRunnable.getAnnotationDocument())) {
                queue.offer(aRunnable);
                log.info("Enqueued new document indexing task: {}", aRunnable);
            }
            else {
                log.debug("No annotation document indexing task enqueued due to a previous "
                        + "enqueued task: {}", aRunnable);
            }
        }
    }

    public void stopAllTasksForUser(String username)
    {
        Iterator<Task> it = queue.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.getUser().getUsername().equals(username)) {
                queue.remove(t);
            }
        }
    }

    public boolean isIndexing(Project p)
    {
        Iterator<Task> it = queue.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.getProject().equals(p)) {
                return true;
            }
        }
        return false;
    }

    public boolean isIndexingDocument(SourceDocument aSourceDocument)
    {
        Iterator<Task> it = queue.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.getProject().equals(aSourceDocument.getProject())
                    && t.getAnnotationDocument().getId() == aSourceDocument.getId()) {
                return true;
            }
        }
        return false;
    }

    public boolean isIndexingDocument(AnnotationDocument aAnnotationDocument)
    {
        Iterator<Task> it = queue.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.getProject().equals(aAnnotationDocument.getProject())
                    && t.getAnnotationDocument().getId() == aAnnotationDocument.getId()) {
                return true;
            }
        }
        return false;
    }

}
