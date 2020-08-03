/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * 
 */
package org.apache.lucene.demo;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.benchmark.byTask.feeds.DemoHTMLParser.Parser;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 *
 */
public class HtmlIndexFiles {

  /**
   * @param args : Generates keyword index for wiki-small
   */
  public static void main(String[] args) throws Exception {
    //Args should be either a path to a document or a folder with documents to parse.
    if (args.length != 1) {
      System.err.println("BADDDDDD USAGEEEEEEE");
    }else {
      String docsPath = args[0];
      Directory indxDir = FSDirectory.open(Paths.get("index"));
      Analyzer analyzer = new StandardAnalyzer();
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
      IndexWriter writer = new IndexWriter(indxDir, iwc);
      
      final Path docDir = Paths.get(docsPath);
      parseDocs(writer,docDir);
      System.out.println("Done");
    }
  }
  
  static public void parseDoc(IndexWriter writer, Path file) throws Exception {
    List<String> lines = Files.readAllLines(file);
    String text = "";
    for (String line:lines) {
      text = text+line;
    }
    Parser parser = new Parser(new StringReader(text));
    //assertEquals("foo", parser.title);
    String title = parser.title;
    String content = parser.body;
    String output = title + " " + content;
    
    Document doc = new Document();
    doc.add(new TextField("title", new StringReader(title)));
    doc.add(new TextField("content", new StringReader(content)));
    Field pathField = new StringField("path", file.toString(), Field.Store.YES);
    doc.add(pathField);
    System.out.println("adding " + file);
    writer.addDocument(doc);
  }
  
  static void parseDocs(IndexWriter writer,Path path) throws Exception {
    if (Files.isDirectory(path)) {
      Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          try {
            parseDoc(writer,file);
          } catch (IOException ignore) {
            // don't index files that can't be read.
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          return FileVisitResult.CONTINUE;
        }
      });
    } else {
      parseDoc(writer,path);
    }
  }
  
}
