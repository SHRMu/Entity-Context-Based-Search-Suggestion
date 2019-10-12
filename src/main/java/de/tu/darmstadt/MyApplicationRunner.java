package de.tu.darmstadt;

import de.tu.darmstadt.utils.FileLoader;
import de.tu.darmstadt.dao.JDBCNews;
import de.tu.darmstadt.model.NewsDoc;
import de.tu.darmstadt.model.UserDoc;
import de.tu.darmstadt.service.EsRestService;
import de.tu.darmstadt.service.ModelPredService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.tensorflow.Graph;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Component
public class MyApplicationRunner implements ApplicationRunner {
    private static Logger logger = Logger.getLogger(MyApplicationRunner.class.getClass());

    @Autowired
    EsRestService restService;

    @Override
    public void run(ApplicationArguments var1) throws Exception {


        if (restService.existIndex("userdoc"))
            restService.deleteIndex("userdoc");

        //mapping
        XContentBuilder builder = null;
        try {
            builder = XContentFactory.jsonBuilder();
            builder.startObject();
            {
                builder.startObject("properties");
                {
                    builder.startObject("title");
                    {
                        builder.field("type", "text");
//                        builder.field("analyzer", "ik_max_word");
                    }
                    builder.endObject();
                    builder.startObject("filecontent");
                    {
                        builder.field("type", "text");
//                        builder.field("analyzer", "ik_max_word");
                        builder.field("term_vector", "with_positions_offsets");
                    }
                    builder.endObject();
                }
                builder.endObject();
            }
            builder.endObject();

            Boolean isSuccess = restService.initIndex("userdoc",
                    "file", 3, 0, builder);

            if (isSuccess) {

                logger.info("init index susscess. index_name: userdoc, type_name: file, shardNum: 3, repliNum: 0");
                /**
                 * 批量导数据
                 */
                Resource resource = new ClassPathResource("files");
                ObjectMapper objMapper = new ObjectMapper();

                ArrayList<String> fileList = new ArrayList<>();
                ApplicationContext context = new ClassPathXmlApplicationContext("beans.xml");
                JDBCNews JDBCNews = (JDBCNews) context.getBean("JDBCNews");

                List<NewsDoc> allDoc = JDBCNews.getWorldNewsDoc();
                for (NewsDoc doc:
                     allDoc) {
                    UserDoc userDoc = new UserDoc();
                    userDoc.setTitle(doc.getNews_title());
                    userDoc.setFilecontent(doc.getNews_fulltext());
                    String json = objMapper.writeValueAsString(userDoc);
                    fileList.add(json);
                    System.out.println(doc.getNews_title());
                }
                System.out.println("selected news doc "+ fileList.size());
                restService.indexDoc("userdoc", "file", fileList);

                //init char and word dictionary
                FileLoader.loadChars();
                FileLoader.loadWords();

                //laod model file and init session
                Graph graph = new Graph();
                graph.importGraphDef(Files.readAllBytes(Paths.get("./src/main/resources/charmodel/char_model.pb")));
                ModelPredService.charSess = new Session(graph);

                SavedModelBundle savedModelBundle = SavedModelBundle.load("./src/main/resources/mymodel","myTag");
                ModelPredService.wordSess = savedModelBundle.session();

            } else {
                logger.error("Failed to init the index...");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
