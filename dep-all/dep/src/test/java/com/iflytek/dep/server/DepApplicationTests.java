package com.iflytek.dep.server;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class DepApplicationTests {

    @Autowired
    private MockMvc mvc;

    @Test
    public void contextLoads() throws Exception {
		/*try {
			mvc.perform(MockMvcRequestBuilders.get("/api/service/qxgl/jsgl/getGnmkAll")
					.contentType(MediaType.APPLICATION_JSON))
					.andExpect(MockMvcResultMatchers.status().isOk());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/

        long startTime = startTest();
        //数据库递归执行 执行时间：27.337s
//		List<GnmkTree> list = service.GetGnmkAll("0");
        //一次读取，JAVA递归执行时间：13.442s
//		List<GnmkTree> list = service.getGnmkTreeAll("0");


        System.out.println("eeeeeeeessssseeeeee");

        //执行时间：29.181s
//		List<HashMap<String, Object>>  list = swjgService.getSwjgTreeN("00000000000");

//		swjgService.getSwjgTreeN("00000000000");

        endTest("DepApplicationTests",startTime);
    }

    public static long startTest() {

        return  System.currentTimeMillis();
    }

    public static void endTest(String caseName,long startTime) {
        long endTime   = System.currentTimeMillis();
        float excTime=(float)(endTime - startTime) / 1000;
        System.out.println("==》》【" + caseName + "】执行时间：" + excTime + "s");
    }
}
