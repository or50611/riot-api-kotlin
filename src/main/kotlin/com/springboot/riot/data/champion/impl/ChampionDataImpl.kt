package com.springboot.riot.data.champion.impl

import com.google.gson.Gson
import com.springboot.riot.data.champion.dto.ChampionDto
import com.springboot.riot.data.champion.mapper.ChampionMapper
import com.springboot.riot.data.champion.service.ChampionDataService
import com.springboot.riot.data.version.dto.VersionDto
import com.springboot.riot.data.version.mapper.VersionMapper
import com.springboot.riot.global.Globals
import com.springboot.riot.global.common.RiotApiUtil
import com.springboot.riot.global.common.RiotFileUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.io.InputStream
import java.io.InputStreamReader
import java.util.*
import javax.servlet.http.HttpServletRequest
import kotlin.collections.HashMap

@Service
class ChampionDataImpl : ChampionDataService {

    var logger: Logger = LoggerFactory.getLogger(ChampionDataService::class.java)

    @Autowired
    lateinit var versionMapper: VersionMapper

    @Autowired
    lateinit var championMapper: ChampionMapper

    override fun championJsonInfo() {

        val findVersion = versionMapper.selectVersionList().firstOrNull { it.title == "champion" }

        val vGson: Gson = Gson()
        val versionDto: VersionDto
        val vInput: InputStream = RiotApiUtil.getUrl(Globals.URL_VERSION_JSON)

        versionDto = vGson.fromJson(InputStreamReader(vInput), VersionDto::class.java)

        versionDto.n.let { version ->
            if(version?.champion == findVersion?.version) {
                return
            }
            logger.info("CHAMPION 업데이트 시작 : {}", version?.champion)

            val gson = Gson()
            val championDto: ChampionDto?
            val input:InputStream = RiotApiUtil.getUrl(Globals.URL_JSON_DATA_PATH+version?.champion+"/data/ko_KR/champion.json")
            championDto = gson.fromJson(InputStreamReader(input), ChampionDto::class.java)
            val uploadPath: String = RiotApiUtil.getDirPath("riotImage/champion/")
            val imageDataPath: String = Globals.URL_JSON_DATA_PATH+version?.champion+"/img/champion/"
            //챔피언이미지데이터
            championDto.getAdditionalProperties()?.forEach { e ->
                RiotFileUtil.imageDownload(imageDataPath,uploadPath,e.key+".png")
            }

            var tagsMap:HashMap<String,String>
            championDto.getAdditionalProperties()?.values?.forEach { dataDto ->
                dataDto.image?.key = dataDto.key
                dataDto.info?.key = dataDto.key
                dataDto.stats?.key = dataDto.key

                championMapper.insertChampionBasic(dataDto)
                dataDto.image?.let { championMapper.insertChampionImage(it) }
                dataDto.info?.let { championMapper.insertChampionInfo(it) }
                dataDto.stats?.let { championMapper.insertChampionStats(it) }

                dataDto.key?.let { championMapper.deleteChampionTags(it) }
                dataDto.tags?.forEach { str ->
                    tagsMap = HashMap()
                    dataDto.key?.let { tagsMap.put("key", it) }
                    tagsMap["tag"] = str

                    championMapper.insertChampionTags(tagsMap)
                }
            }

            var inSpells: InputStream
            var championSpellDto: ChampionDto?

            val spellsUploadPath: String = RiotApiUtil.getDirPath("riotImage/spells/")
            val spellsImagePath: String = Globals.URL_JSON_DATA_PATH + version?.champion + "/img/spell/"

            val passiveUploadPath: String = RiotApiUtil.getDirPath("riotImage/passive/")
            val passiveImagePath: String = Globals.URL_JSON_DATA_PATH + version?.champion + "/img/passive/"

            var imgNm: String?
            var count: Int

            //챔피언스킬, 패시브
            championDto.getAdditionalProperties()?.forEach { e ->

                inSpells = RiotApiUtil.getUrl(Globals.URL_JSON_DATA_PATH+version?.champion+"/data/ko_KR/champion/"+e.key+".json")
                championSpellDto = gson.fromJson(InputStreamReader(inSpells), ChampionDto::class.java)

                championSpellDto?.getAdditionalProperties()?.values?.forEach { dataDto ->

                    //스킬
                    count = 0
                    dataDto.spells?.forEach { spells ->
                        count++
                        spells.skillSlot = count
                        spells.key = dataDto.key
                        spells.image?.key = dataDto.key
                        championMapper.insertChampionSpellBasic(spells)

                        imgNm = spells.image?.full
                        spells.image?.skillSlot = spells.skillSlot
                        spells.image?.let { championMapper.insertChampionSpellImage(it) }
                        imgNm?.let { RiotFileUtil.imageDownload(spellsImagePath,spellsUploadPath, it) }
                    }

                    dataDto.passive?.key = dataDto.key
                    dataDto.passive?.image?.key = dataDto.key

                    dataDto.passive?.let { championMapper.insertChampionPassiveBasic(it) }
                    dataDto.passive?.image?.let { championMapper.insertChampionPassiveImage(it) }

                    imgNm = dataDto.passive?.image?.full
                    imgNm?.let { RiotFileUtil.imageDownload(passiveImagePath,passiveUploadPath, it) }

                }
            }

            val dataMap = mutableMapOf("title" to "champion", "version" to version?.champion)

            versionMapper.updateVersionInfo(dataMap);

            logger.info("CHAMPION 업데이트 종료 : {}", version?.champion)

        }

    }
}