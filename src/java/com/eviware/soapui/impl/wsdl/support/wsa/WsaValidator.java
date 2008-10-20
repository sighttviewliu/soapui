/*
 *  soapUI, copyright (C) 2004-2008 eviware.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */
package com.eviware.soapui.impl.wsdl.support.wsa;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.AnonymousTypeConfig;
import com.eviware.soapui.impl.wsdl.WsdlOperation;
import com.eviware.soapui.impl.wsdl.panels.teststeps.support.WsaAssertionConfiguration;
import com.eviware.soapui.impl.wsdl.submit.WsdlMessageExchange;
import com.eviware.soapui.impl.wsdl.support.soap.SoapUtils;
import com.eviware.soapui.impl.wsdl.support.soap.SoapVersion;
import com.eviware.soapui.impl.wsdl.support.wsdl.WsdlUtils;
import com.eviware.soapui.model.testsuite.AssertionError;
import com.eviware.soapui.model.testsuite.AssertionException;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.xml.XmlUtils;

/**
 * Validating class for WS Addressing implemented according to WSDL 1.1
 * specification
 * 
 * @author dragica.soldo
 * @see {@link}http://www.w3.org/TR/2006/WD-ws-addr-wsdl-20060216/#WSDL11MEPS
 */
public class WsaValidator
{
	WsdlMessageExchange messageExchange;
	Element header;
	String wsaVersionNameSpace;
	StringBuilder cumulativeErrorMsg;
	WsaAssertionConfiguration wsaAssertionConfiguration;

	public WsaValidator(WsdlMessageExchange messageExchange, WsaAssertionConfiguration wsaAssertionConfiguration)
	{
		this.messageExchange = messageExchange;
		this.wsaAssertionConfiguration = wsaAssertionConfiguration;
		cumulativeErrorMsg = new StringBuilder();
	}

	public static String getWsaVersion(XmlObject contentObject, SoapVersion soapVersion) 
	{
		String wsaVns = null;
		try
		{
//			XmlObject xmlObject = XmlObject.Factory.parse( content );
			XmlObject[] envS = contentObject.selectChildren( soapVersion.getEnvelopeQName() );
			Element envelope = (Element) envS[0].getDomNode();

			Element hdr = (Element) SoapUtils.getHeaderElement( contentObject, soapVersion, true ).getDomNode();

			if( !hdr.hasChildNodes() )
			{
			   return null;
			}

			String wsaNameSpace = XmlUtils.findPrefixForNamespace(hdr, WsaUtils.WS_A_VERSION_200508 );
			if (wsaNameSpace != null)
			{
				wsaVns = WsaUtils.WS_A_VERSION_200508;
			} else {
				wsaNameSpace = XmlUtils.findPrefixForNamespace(hdr, WsaUtils.WS_A_VERSION_200408 );
				if (wsaNameSpace != null)
				{
					wsaVns = WsaUtils.WS_A_VERSION_200408;
				} else {
					wsaNameSpace = XmlUtils.findPrefixForNamespace(envelope, WsaUtils.WS_A_VERSION_200508 );
					if (wsaNameSpace != null)
					{
						wsaVns = WsaUtils.WS_A_VERSION_200508;
					} else {
						wsaNameSpace = XmlUtils.findPrefixForNamespace(envelope, WsaUtils.WS_A_VERSION_200408 );
						if (wsaNameSpace != null)
						{
							wsaVns = WsaUtils.WS_A_VERSION_200408;
						} else {
							return null;
						}
					}
				}
			}
		}
		catch (XmlException e)
		{
			SoapUI.logError(e);
		}
		return wsaVns;
	}
	private void validateWsAddressingCommon( String content ) 
   {
         if (wsaAssertionConfiguration.isAssertTo())
			{
				// To is Mandatory
				Element toNode = XmlUtils.getFirstChildElementNS(header, wsaVersionNameSpace, "To");
				if (toNode == null)
				{
					cumulativeErrorMsg.append("WS-A To property is not specified. ");
				}
				else
				{
					String toAddressValue = XmlUtils.getElementText(toNode);
					if (StringUtils.isNullOrEmpty(toAddressValue))
					{
						cumulativeErrorMsg.append("WS-A To property is empty. ");
					}
					else
					{
						// check for anonymous - in case of mock response to=request.replyTo
						if (AnonymousTypeConfig.PROHIBITED.toString().equals(messageExchange.getOperation().getAnonymous())
								&& WsaUtils.isAnonymousAddress(toAddressValue, wsaVersionNameSpace))
						{
							cumulativeErrorMsg
									.append("WS-A InvalidAddressingHeader To , Anonymous addresses are prohibited. ");
						}
					}
				}
			}
			// if fault_to is specified check if anonymous allowed
         Element faultToNode = XmlUtils.getFirstChildElementNS( header, wsaVersionNameSpace, "FaultTo" );
         if( faultToNode != null )
         {
            Element addressNode = XmlUtils.getFirstChildElementNS( faultToNode, wsaVersionNameSpace, "Address" );
            if( addressNode != null )
            {
               String faultToAddressValue = XmlUtils.getElementText( addressNode );
               if( !StringUtils.isNullOrEmpty( faultToAddressValue ) )
               {
                  // check for anonymous
                  if( AnonymousTypeConfig.PROHIBITED.toString().equals( messageExchange.getOperation().getAnonymous() )
                          && WsaUtils.isAnonymousAddress(faultToAddressValue,wsaVersionNameSpace) )
                  {
                  	cumulativeErrorMsg.append("WS-A InvalidAddressingHeader FaultTo , Anonymous addresses are prohibited. ");
                  } else if (AnonymousTypeConfig.REQUIRED.toString().equals( ((WsdlMessageExchange) messageExchange).getOperation().getAnonymous() )
                        && !(WsaUtils.isAnonymousAddress(faultToAddressValue,wsaVersionNameSpace) || WsaUtils.isNoneAddress(faultToAddressValue,wsaVersionNameSpace)))
                  {
                  	cumulativeErrorMsg.append("WS-A InvalidAddressingHeader FaultTo , Anonymous addresses are required. ");
                  }
               }
            }
         }

   }


	public void validateWsAddressingRequest() throws AssertionException, XmlException
	{
		String content = messageExchange.getRequestContent();
		SoapVersion soapVersion = messageExchange.getOperation().getInterface()
      .getSoapVersion();

		XmlObject xmlObject = XmlObject.Factory.parse( content );
		header = (Element) SoapUtils.getHeaderElement( xmlObject, soapVersion, true ).getDomNode();
		
		wsaVersionNameSpace = getWsaVersion(xmlObject, soapVersion);
		if (wsaVersionNameSpace == null)
		{
			throw new AssertionException( new AssertionError( "WS-A not enabled" ) );
		}

		WsdlOperation operation = messageExchange.getOperation();

      if (wsaAssertionConfiguration.isAssertAction())
		{
			// Action is Mandatory
			Element actionNode = XmlUtils.getFirstChildElementNS(header, wsaVersionNameSpace, "Action");
			if (actionNode == null)
			{
				cumulativeErrorMsg.append("WS-A Action property is not specified. ");
			}
			String actionValue = XmlUtils.getElementText(actionNode);
			if (StringUtils.isNullOrEmpty(actionValue))
			{
				cumulativeErrorMsg.append("WS-A Action property is empty. ");
			}
		}
		validateWsAddressingCommon(content);
		if (operation.isRequestResponse())
		{
			// MessageId is Mandatory
			Element msgNode = XmlUtils.getFirstChildElementNS(header, wsaVersionNameSpace, "MessageID");
			if (msgNode == null)
			{
				cumulativeErrorMsg.append("WS-A MessageID property is not specified. ");
			}
			String msgValue = XmlUtils.getElementText(msgNode);
			if (StringUtils.isNullOrEmpty(msgValue))
			{
				cumulativeErrorMsg.append("WS-A MessageID property is empty");
			}

			// ReplyTo is Mandatory
			Element replyToNode = XmlUtils.getFirstChildElementNS(header, wsaVersionNameSpace, "ReplyTo");
			if (replyToNode == null)
			{
				cumulativeErrorMsg.append("WS-A ReplyTo property is not specified. ");
			}
			Element addressNode = XmlUtils.getFirstChildElementNS(replyToNode, wsaVersionNameSpace, "Address");
			if (addressNode == null)
			{
				cumulativeErrorMsg.append("WS-A ReplyTo Address property is not specified. ");
			}
			String replyToAddressValue = XmlUtils.getElementText(addressNode);
			if (StringUtils.isNullOrEmpty(replyToAddressValue))
			{
				cumulativeErrorMsg.append("WS-A ReplyTo Address property is empty. ");
			} else {
            // check for anonymous
            if( AnonymousTypeConfig.PROHIBITED.toString().equals( ((WsdlMessageExchange) messageExchange).getOperation().getAnonymous() )
                    && WsaUtils.isAnonymousAddress(replyToAddressValue,wsaVersionNameSpace) )
            {
            	cumulativeErrorMsg.append("WS-A InvalidAddressingHeader ReplyTo , Anonymous addresses are prohibited. ");
            } else if (AnonymousTypeConfig.REQUIRED.toString().equals( ((WsdlMessageExchange) messageExchange).getOperation().getAnonymous() )
                  && !(WsaUtils.isAnonymousAddress(replyToAddressValue,wsaVersionNameSpace)|| WsaUtils.isNoneAddress(replyToAddressValue,wsaVersionNameSpace)))
            {
            	cumulativeErrorMsg.append("WS-A InvalidAddressingHeader ReplyTo , Anonymous addresses are required. ");
            }
			}
		}
      String cumulativeError = cumulativeErrorMsg.toString();
      if (!StringUtils.isNullOrEmpty(cumulativeError))
		{
         throw new AssertionException(new AssertionError(cumulativeError));
		}
	}

	public void validateWsAddressingResponse() throws AssertionException, XmlException
	{
		String content = messageExchange.getResponseContent();
		SoapVersion soapVersion = messageExchange.getOperation().getInterface()
      .getSoapVersion();

		XmlObject requestXmlObject = XmlObject.Factory.parse( messageExchange.getRequestContent() );
		XmlObject xmlObject = XmlObject.Factory.parse( content );
		header = (Element) SoapUtils.getHeaderElement( xmlObject, soapVersion, true ).getDomNode();
		
      wsaVersionNameSpace = getWsaVersion(xmlObject, soapVersion);
      String requestWsaVersionNameSpace = getWsaVersion(requestXmlObject, soapVersion);
		if (wsaVersionNameSpace == null)
		{
			throw new AssertionException( new AssertionError( "WS-A not enabled." ) );
		} else if (!wsaVersionNameSpace.equals(requestWsaVersionNameSpace))
		{
			throw new AssertionException( new AssertionError( "Response has the wrong ws-a version namespace value." ) );
		}

      if (wsaAssertionConfiguration.isAssertAction())
		{
			// Action is Mandatory
			Element actionNode = XmlUtils.getFirstChildElementNS(header, wsaVersionNameSpace, "Action");
			if (actionNode == null)
			{
				cumulativeErrorMsg.append("WS-A Action property is not specified. ");
			}
			else
			{
				String actionValue = XmlUtils.getElementText(actionNode);
				if (StringUtils.isNullOrEmpty(actionValue))
				{
					cumulativeErrorMsg.append("WS-A Action property is empty. ");
				}
				else
				{
					String defaultWsdlAction = WsdlUtils.getDefaultWsaAction(messageExchange.getOperation(), true);
					if (!actionValue.equals(defaultWsdlAction))
					{
						cumulativeErrorMsg.append("WS-A Action property should be " + defaultWsdlAction + ". ");
					}
				}
			}
		}
		validateWsAddressingCommon(content);

		if (wsaAssertionConfiguration.isAssertRelatesTo())
		{
			// RelatesTo is Mandatory
			Element relatesToNode = XmlUtils.getFirstChildElementNS(header, wsaVersionNameSpace, "RelatesTo");
			if (relatesToNode == null)
			{
				cumulativeErrorMsg.append("WS-A RelatesTo property is not specified. ");
			}
			else
			{
				String relatesToValue = XmlUtils.getElementText(relatesToNode);
				if (StringUtils.isNullOrEmpty(relatesToValue))
				{
					cumulativeErrorMsg.append("WS-A RelatesTo property is empty. ");
				}
				else
				{
					String requestMsgId = WsdlUtils.getRequestWsaMessageId(messageExchange, getWsaVersion(requestXmlObject,
							soapVersion));
					if (!relatesToValue.equals(requestMsgId))
					{
						cumulativeErrorMsg.append("WS-A RelatesTo property is not equal to request wsa:MessageId. ");
					}
				}
				/*
				 * When absent, the implied value of this attribute is "http://www.w3.org/2005/08/addressing/reply".
				 * question is does it have to be present as 'reply' ???
				 */

				//			String relationshipType = relatesToNode.getAttribute("RelationshipType");
				//			if (StringUtils.isNullOrEmpty(relationshipType))
				//			{
				//				relationshipType = relatesToNode.getAttributeNS(WsaUtils.WS_A_VERSION_200508, "RelationshipType");
				//				if (StringUtils.isNullOrEmpty(relationshipType))
				//				{
				//					relationshipType = relatesToNode.getAttributeNS(WsaUtils.WS_A_VERSION_200408, "RelationshipType");
				//					if (StringUtils.isNullOrEmpty(relationshipType))
				//					{
				//						cumulativeErrorMsg.append("WS-A RelationshipType is not specified. ");
				//					}
				//				} 
				//			} 
			}
		}
		// if fault_to is specified check if anonymous allowed
      Element replyToNode = XmlUtils.getFirstChildElementNS( header, wsaVersionNameSpace, "ReplyTo" );
      if( replyToNode != null )
      {
         Element addressNode = XmlUtils.getFirstChildElementNS( replyToNode, wsaVersionNameSpace, "Address" );
         if( addressNode != null )
         {
            String replyToAddressValue = XmlUtils.getElementText( addressNode );
            if( !StringUtils.isNullOrEmpty( replyToAddressValue ) )
            {
               // check for anonymous
               if( AnonymousTypeConfig.PROHIBITED.toString().equals( ((WsdlMessageExchange) messageExchange).getOperation().getAnonymous() )
                       && WsaUtils.isAnonymousAddress(replyToAddressValue,wsaVersionNameSpace) )
               {
               	cumulativeErrorMsg.append("WS-A InvalidAddressingHeader ReplyTo , Anonymous addresses are prohibited. ");
               } else if (AnonymousTypeConfig.REQUIRED.toString().equals( ((WsdlMessageExchange) messageExchange).getOperation().getAnonymous() )
                     && !(WsaUtils.isAnonymousAddress(replyToAddressValue,wsaVersionNameSpace) || WsaUtils.isNoneAddress(replyToAddressValue,wsaVersionNameSpace)))
               {
               	cumulativeErrorMsg.append("WS-A InvalidAddressingHeader ReplyTo , Anonymous addresses are required. ");
               }
            }
         }
      }
      if (wsaAssertionConfiguration.isAssertReplyToRefParams()) {
		//check if request ReplyTo ReferenceParameters are included in response 
		NodeList requestReplyToRefProps = WsdlUtils.getRequestReplyToRefProps(
				messageExchange, getWsaVersion(requestXmlObject, soapVersion));
		for (int i = 0; i < requestReplyToRefProps.getLength(); i++) {
			Node refProp = requestReplyToRefProps.item(i);
			String refPropName = refProp.getNodeName();
			NodeList existingResponseRefs = XmlUtils.getChildElementsByTagName(header, refPropName);
			if (existingResponseRefs != null
					&& existingResponseRefs.getLength() > 0) {
				continue;
			} else {
				cumulativeErrorMsg.append("Response does not have request ReferenceProperty "+ refPropName + ". ");
			}

		}
      }
      if (wsaAssertionConfiguration.isAssertFaultToRefParams()) {
  		//check if request FaultTo ReferenceParameters are included in response 
  		NodeList requestFaultToRefProps = WsdlUtils.getRequestFaultToRefProps(
  				messageExchange, getWsaVersion(requestXmlObject, soapVersion));
  		for (int i = 0; i < requestFaultToRefProps.getLength(); i++) {
  			Node refProp = requestFaultToRefProps.item(i);
  			String refPropName = refProp.getNodeName();
  			NodeList existingResponseRefs = XmlUtils.getChildElementsByTagName(header, refPropName);
  			if (existingResponseRefs != null
  					&& existingResponseRefs.getLength() > 0) {
  				continue;
  			} else {
  				cumulativeErrorMsg.append("Response does not have request ReferenceProperty "+ refPropName + ". ");
  			}

  		}
  	}
	String cumulativeError = cumulativeErrorMsg.toString();
      if (!StringUtils.isNullOrEmpty(cumulativeError))
		{
         throw new AssertionException(new AssertionError(cumulativeError));
		}
	}
}
