package org.healthflow.dp.core.util

import io.jsonwebtoken.Jwts
import org.healthflow.dp.core.job.BaseJobConfig

import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.{Base64, Date, UUID}
import java.{lang, util}

class JWTUtil(config: BaseJobConfig) extends Serializable {

  def generateHCXGatewayToken(): String = {
    val date = new Date().getTime.asInstanceOf[lang.Long]
    val headers = new util.HashMap[String, AnyRef]()
    headers.put(Constants.TYP, Constants.JWT)
    val payload = new util.HashMap[String, AnyRef]()
    payload.put(Constants.JTI, UUID.randomUUID().toString)
    payload.put(Constants.ISS, config.hcxRegistryCode)
    payload.put(Constants.SUB, config.hcxRegistryCode)
    payload.put(Constants.IAT, date)
    payload.put(Constants.EXP, new Date(date + config.expiryTime).getTime.asInstanceOf[lang.Long])
    generateJWS(headers, payload)
  }

  def generateJWS(headers: util.Map[String, AnyRef], payload: util.Map[String, AnyRef]): String = {
    val privateKeyDecoded = Base64.getDecoder.decode(config.privateKey)
    val spec = new PKCS8EncodedKeySpec(privateKeyDecoded)
    val privateKey = KeyFactory.getInstance("RSA").generatePrivate(spec)
    Jwts.builder
      .header.add(headers).and
      .claims.add(payload).and
      .signWith(privateKey, Jwts.SIG.RS256)
      .compact
  }

}