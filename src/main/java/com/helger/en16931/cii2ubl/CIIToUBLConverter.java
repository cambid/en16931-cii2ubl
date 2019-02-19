package com.helger.en16931.cii2ubl;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.datatype.XMLGregorianCalendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.cii.d16b.CIID16BReader;
import com.helger.commons.datetime.PDTFromString;
import com.helger.commons.error.list.ErrorList;
import com.helger.commons.math.MathHelper;
import com.helger.commons.string.StringHelper;
import com.helger.datetime.util.PDTXMLConverter;
import com.helger.jaxb.validation.WrappedCollectingValidationEventHandler;

import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_21.*;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.CompanyIDType;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.DocumentDescriptionType;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.EmbeddedDocumentBinaryObjectType;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.EndpointIDType;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.NameType;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.PaymentIDType;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.PaymentMeansCodeType;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.PrimaryAccountNumberIDType;
import oasis.names.specification.ubl.schema.xsd.creditnote_21.CreditNoteType;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;
import un.unece.uncefact.data.standard.crossindustryinvoice._100.CrossIndustryInvoiceType;
import un.unece.uncefact.data.standard.qualifieddatatype._100.FormattedDateTimeType;
import un.unece.uncefact.data.standard.reusableaggregatebusinessinformationentity._100.*;
import un.unece.uncefact.data.standard.unqualifieddatatype._100.AmountType;
import un.unece.uncefact.data.standard.unqualifieddatatype._100.BinaryObjectType;
import un.unece.uncefact.data.standard.unqualifieddatatype._100.DateTimeType;
import un.unece.uncefact.data.standard.unqualifieddatatype._100.IDType;
import un.unece.uncefact.data.standard.unqualifieddatatype._100.TextType;

public class CIIToUBLConverter
{
  private static final Logger LOGGER = LoggerFactory.getLogger (CIIToUBLConverter.class);

  public CIIToUBLConverter ()
  {}

  /**
   * Copy all ID parts from a CII ID to a CCTS/UBL ID.
   *
   * @param aCIIID
   *        CII ID
   * @param aUBLID
   *        UBL ID
   * @return Created UBL ID
   */
  @Nullable
  private static <T extends com.helger.xsds.ccts.cct.schemamodule.IdentifierType> T _copyID (@Nullable final IDType aCIIID,
                                                                                             @Nonnull final T aUBLID)
  {
    if (aCIIID == null)
      return null;
    aUBLID.setValue (aCIIID.getValue ());
    aUBLID.setSchemeID (aCIIID.getSchemeID ());
    aUBLID.setSchemeName (aCIIID.getSchemeName ());
    aUBLID.setSchemeAgencyID (aCIIID.getSchemeAgencyID ());
    aUBLID.setSchemeAgencyName (aCIIID.getSchemeAgencyName ());
    aUBLID.setSchemeVersionID (aCIIID.getSchemeVersionID ());
    aUBLID.setSchemeDataURI (aCIIID.getSchemeDataURI ());
    aUBLID.setSchemeURI (aCIIID.getSchemeURI ());
    return aUBLID;
  }

  @Nullable
  private static oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.IDType _copyID (@Nullable final IDType aCIIID)
  {
    return _copyID (aCIIID, new oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.IDType ());
  }

  @Nullable
  private static NameType _copyName (@Nullable final TextType aName)
  {
    if (aName == null)
      return null;

    final NameType ret = new NameType ();
    ret.setValue (aName.getValue ());
    ret.setLanguageID (aName.getLanguageID ());
    ret.setLanguageLocaleID (aName.getLanguageLocaleID ());
    return ret;
  }

  @Nullable
  private static XMLGregorianCalendar _parseDateDDMMYYYY (@Nullable final String s)
  {
    final LocalDate aDate = PDTFromString.getLocalDateFromString (s, "uuMMdd");
    return PDTXMLConverter.getXMLCalendarDate (aDate);
  }

  @Nullable
  private static DocumentReferenceType _convertDocumentReference (@Nullable final ReferencedDocumentType aRD)
  {
    DocumentReferenceType ret = null;
    if (aRD != null)
    {
      final String sID = aRD.getIssuerAssignedIDValue ();
      if (StringHelper.hasText (sID))
      {
        ret = new DocumentReferenceType ();
        // ID value is a mandatory field
        ret.setID (sID).setSchemeID (aRD.getReferenceTypeCodeValue ());

        // IssueDate is optional
        final FormattedDateTimeType aFDT = aRD.getFormattedIssueDateTime ();
        if (aFDT != null)
          ret.setIssueDate (_parseDateDDMMYYYY (aFDT.getDateTimeStringValue ()));

        // Name is optional
        for (final TextType aItem : aRD.getName ())
        {
          final DocumentDescriptionType aUBLDocDesc = new DocumentDescriptionType ();
          aUBLDocDesc.setValue (aItem.getValue ());
          aUBLDocDesc.setLanguageID (aItem.getLanguageID ());
          aUBLDocDesc.setLanguageLocaleID (aItem.getLanguageLocaleID ());
          ret.addDocumentDescription (aUBLDocDesc);
        }

        // Attachment (0..1 for CII)
        if (aRD.getAttachmentBinaryObjectCount () > 0)
        {
          final BinaryObjectType aBinObj = aRD.getAttachmentBinaryObjectAtIndex (0);

          final AttachmentType aUBLAttachment = new AttachmentType ();
          final EmbeddedDocumentBinaryObjectType aEmbeddedDoc = new EmbeddedDocumentBinaryObjectType ();
          aEmbeddedDoc.setMimeCode (aBinObj.getMimeCode ());
          aEmbeddedDoc.setFilename (aBinObj.getFilename ());
          aUBLAttachment.setEmbeddedDocumentBinaryObject (aEmbeddedDoc);

          final String sURI = aRD.getURIIDValue ();
          if (StringHelper.hasText (sURI))
          {
            final ExternalReferenceType aUBLExtRef = new ExternalReferenceType ();
            aUBLExtRef.setURI (sURI);
            aUBLAttachment.setExternalReference (aUBLExtRef);
          }
          ret.setAttachment (aUBLAttachment);
        }
      }
    }
    return ret;
  }

  @Nonnull
  private static AddressType _convertPostalAddress (@Nonnull final TradeAddressType aPostalAddress)
  {
    final AddressType ret = new AddressType ();
    ret.setStreetName (aPostalAddress.getLineOneValue ());
    ret.setAdditionalStreetName (aPostalAddress.getLineTwoValue ());
    if (StringHelper.hasText (aPostalAddress.getLineThreeValue ()))
    {
      final AddressLineType aUBLAddressLine = new AddressLineType ();
      aUBLAddressLine.setLine (aPostalAddress.getLineThreeValue ());
      ret.addAddressLine (aUBLAddressLine);
    }
    ret.setCityName (aPostalAddress.getCityNameValue ());
    ret.setPostalZone (aPostalAddress.getPostcodeCodeValue ());
    if (aPostalAddress.hasCountrySubDivisionNameEntries ())
      ret.setCountrySubentity (aPostalAddress.getCountrySubDivisionNameAtIndex (0).getValue ());
    if (StringHelper.hasText (aPostalAddress.getCountryIDValue ()))
    {
      final CountryType aUBLCountry = new CountryType ();
      aUBLCountry.setIdentificationCode (aPostalAddress.getCountryIDValue ());
      ret.setCountry (aUBLCountry);
    }
    return ret;
  }

  @Nullable
  private static oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.IDType _extractPartyID (@Nonnull final TradePartyType aParty)
  {
    IDType aID;
    if (aParty.hasGlobalIDEntries ())
      aID = aParty.getGlobalIDAtIndex (0);
    else
      if (aParty.hasIDEntries ())
        aID = aParty.getIDAtIndex (0);
      else
        aID = null;

    return aID == null ? null : _copyID (aID);
  }

  @Nonnull
  private static PartyType _convertParty (@Nonnull final TradePartyType aParty)
  {
    final PartyType ret = new PartyType ();

    if (aParty.hasURIUniversalCommunicationEntries ())
    {
      final UniversalCommunicationType UC = aParty.getURIUniversalCommunicationAtIndex (0);
      ret.setEndpointID (_copyID (UC.getURIID (), new EndpointIDType ()));
    }

    final oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.IDType aUBLID = _extractPartyID (aParty);
    if (aUBLID != null)
    {
      final PartyIdentificationType aUBLPartyIdentification = new PartyIdentificationType ();
      aUBLPartyIdentification.setID (aUBLID);
      ret.addPartyIdentification (aUBLPartyIdentification);
    }

    final TextType aName = aParty.getName ();
    if (aName != null)
    {
      final PartyNameType aUBLPartyName = new PartyNameType ();
      aUBLPartyName.setName (_copyName (aName));
      ret.addPartyName (aUBLPartyName);
    }

    final TradeAddressType aPostalAddress = aParty.getPostalTradeAddress ();
    if (aPostalAddress != null)
    {
      ret.setPostalAddress (_convertPostalAddress (aPostalAddress));
    }

    return ret;
  }

  @Nonnull
  private static PartyTaxSchemeType _convertPartyTaxScheme (@Nonnull final TaxRegistrationType aTaxRegistration)
  {
    final PartyTaxSchemeType aUBLPartyTaxScheme = new PartyTaxSchemeType ();
    aUBLPartyTaxScheme.setCompanyID (_copyID (aTaxRegistration.getID (), new CompanyIDType ()));
    final TaxSchemeType aUBLTaxScheme = new TaxSchemeType ();
    aUBLTaxScheme.setID ("VAT");
    aUBLPartyTaxScheme.setTaxScheme (aUBLTaxScheme);
    return aUBLPartyTaxScheme;
  }

  @Nullable
  private static PartyLegalEntityType _convertPartyLegalEntity (@Nonnull final TradePartyType aTradeParty)
  {
    final PartyLegalEntityType aUBLPartyLegalEntity = new PartyLegalEntityType ();
    boolean bAnyValueSet = false;

    final LegalOrganizationType aSLO = aTradeParty.getSpecifiedLegalOrganization ();
    if (aSLO != null)
    {
      aUBLPartyLegalEntity.setRegistrationName (aSLO.getTradingBusinessNameValue ());
      aUBLPartyLegalEntity.setCompanyID (_copyID (aSLO.getID (), new CompanyIDType ()));
      bAnyValueSet = true;
    }

    for (final TextType aDesc : aTradeParty.getDescription ())
      if (StringHelper.hasText (aDesc.getValue ()))
      {
        // Use the first only
        aUBLPartyLegalEntity.setCompanyLegalForm (aDesc.getValue ());
        bAnyValueSet = true;
        break;
      }

    if (!bAnyValueSet)
      return null;
    return aUBLPartyLegalEntity;
  }

  @Nullable
  private static ContactType _convertContact (@Nonnull final TradePartyType aTradeParty)
  {
    if (!aTradeParty.hasDefinedTradeContactEntries ())
      return null;

    final TradeContactType aDTC = aTradeParty.getDefinedTradeContactAtIndex (0);
    final ContactType aUBLContact = new ContactType ();
    aUBLContact.setName (_copyName (aDTC.getPersonName ()));

    final UniversalCommunicationType aTel = aDTC.getTelephoneUniversalCommunication ();
    if (aTel != null)
      aUBLContact.setTelephone (aTel.getCompleteNumberValue ());

    final UniversalCommunicationType aEmail = aDTC.getEmailURIUniversalCommunication ();
    if (aEmail != null)
      aUBLContact.setElectronicMail (aEmail.getURIIDValue ());

    return aUBLContact;
  }

  @Nonnull
  private InvoiceType _convertToInvoice (@Nonnull final CrossIndustryInvoiceType aCIIInvoice,
                                         @Nonnull final ErrorList aErrorList)
  {
    final ExchangedDocumentContextType aEDC = aCIIInvoice.getExchangedDocumentContext ();
    final ExchangedDocumentType aED = aCIIInvoice.getExchangedDocument ();
    final SupplyChainTradeTransactionType aSCTT = aCIIInvoice.getSupplyChainTradeTransaction ();
    final HeaderTradeAgreementType aAgreement = aSCTT.getApplicableHeaderTradeAgreement ();
    final HeaderTradeDeliveryType aDelivery = aSCTT.getApplicableHeaderTradeDelivery ();
    final HeaderTradeSettlementType aSettlement = aSCTT.getApplicableHeaderTradeSettlement ();

    final InvoiceType aUBLInvoice = new InvoiceType ();
    aUBLInvoice.setUBLVersionID ("2.1");
    aUBLInvoice.setCustomizationID ("urn:cen.eu:en16931:2017:extended:urn:fdc:peppol.eu:2017:poacc:billing:3.0");
    aUBLInvoice.setProfileID ("urn:fdc:peppol.eu:2017:poacc:billing:01:1.0");
    aUBLInvoice.setID (aED.getIDValue ());

    // Mandatory supplier
    final SupplierPartyType aUBLSupplier = new SupplierPartyType ();
    aUBLInvoice.setAccountingSupplierParty (aUBLSupplier);

    // Mandatory customer
    final CustomerPartyType aUBLCustomer = new CustomerPartyType ();
    aUBLInvoice.setAccountingCustomerParty (aUBLCustomer);

    // IssueDate
    {
      XMLGregorianCalendar aIssueDate = null;
      if (aED.getIssueDateTime () != null)
        aIssueDate = _parseDateDDMMYYYY (aED.getIssueDateTime ().getDateTimeStringValue ());
      if (aIssueDate == null)
        for (final TradePaymentTermsType aPaymentTerms : aSettlement.getSpecifiedTradePaymentTerms ())
          if (aPaymentTerms.getDueDateDateTime () != null)
          {
            aIssueDate = _parseDateDDMMYYYY (aPaymentTerms.getDueDateDateTime ().getDateTimeStringValue ());
            if (aIssueDate != null)
              break;
          }
      aUBLInvoice.setIssueDate (aIssueDate);
    }

    // InvoiceTypeCode
    aUBLInvoice.setInvoiceTypeCode (aED.getTypeCodeValue ());

    // Note
    {
      for (final NoteType aEDNote : aED.getIncludedNote ())
      {
        final oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.NoteType aUBLNote = new oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.NoteType ();
        final StringBuilder aSB = new StringBuilder ();
        for (final TextType aText : aEDNote.getContent ())
        {
          if (aSB.length () > 0)
            aSB.append ('\n');
          aSB.append (aText.getValue ());
        }
        aUBLNote.setValue (aSB.toString ());
        aUBLInvoice.addNote (aUBLNote);
      }
    }

    // TaxPointDate
    for (final TradeTaxType aTradeTax : aSettlement.getApplicableTradeTax ())
    {
      if (aTradeTax.getTaxPointDate () != null)
      {
        final XMLGregorianCalendar aTaxPointDate = _parseDateDDMMYYYY (aTradeTax.getTaxPointDate ()
                                                                                .getDateStringValue ());
        if (aTaxPointDate != null)
        {
          // Use the first tax point date only
          aUBLInvoice.setTaxPointDate (aTaxPointDate);
          break;
        }
      }
    }

    // DocumentCurrencyCode
    aUBLInvoice.setDocumentCurrencyCode (aSettlement.getInvoiceCurrencyCodeValue ());

    // TaxCurrencyCode
    aUBLInvoice.setTaxCurrencyCode (aSettlement.getTaxCurrencyCodeValue ());

    // AccountingCost
    for (final TradeAccountingAccountType aAccount : aSettlement.getReceivableSpecifiedTradeAccountingAccount ())
    {
      final String sID = aAccount.getIDValue ();
      if (StringHelper.hasText (sID))
      {
        // Use the first ID
        aUBLInvoice.setAccountingCost (sID);
        break;
      }
    }

    // BuyerReferences
    aUBLInvoice.setBuyerReference (aAgreement.getBuyerReferenceValue ());

    // InvoicePeriod
    {
      final SpecifiedPeriodType aSPT = aSettlement.getBillingSpecifiedPeriod ();
      if (aSPT != null)
      {
        final DateTimeType aStartDT = aSPT.getStartDateTime ();
        final DateTimeType aEndDT = aSPT.getEndDateTime ();

        if (aStartDT != null && aEndDT != null)
        {
          final PeriodType aUBLPeriod = new PeriodType ();
          aUBLPeriod.setStartDate (_parseDateDDMMYYYY (aStartDT.getDateTimeStringValue ()));
          aUBLPeriod.setEndDate (_parseDateDDMMYYYY (aEndDT.getDateTimeStringValue ()));
          aUBLInvoice.addInvoicePeriod (aUBLPeriod);
        }
      }
    }

    // OrderReference
    {
      final OrderReferenceType aUBLOrderRef = new OrderReferenceType ();
      final ReferencedDocumentType aBuyerOrderRef = aAgreement.getBuyerOrderReferencedDocument ();
      if (aBuyerOrderRef != null)
        aUBLOrderRef.setID (aBuyerOrderRef.getIssuerAssignedIDValue ());
      final ReferencedDocumentType aSellerOrderRef = aAgreement.getSellerOrderReferencedDocument ();
      if (aSellerOrderRef != null)
        aUBLOrderRef.setSalesOrderID (aSellerOrderRef.getIssuerAssignedIDValue ());

      // Set if any field is set
      if (aUBLOrderRef.getIDValue () != null || aUBLOrderRef.getSalesOrderIDValue () != null)
        aUBLInvoice.setOrderReference (aUBLOrderRef);
    }

    // BillingReference
    {
      final DocumentReferenceType aUBLDocRef = _convertDocumentReference (aSettlement.getInvoiceReferencedDocument ());
      if (aUBLDocRef != null)
      {
        final BillingReferenceType aUBLBillingRef = new BillingReferenceType ();
        aUBLBillingRef.setInvoiceDocumentReference (aUBLDocRef);
        aUBLInvoice.addBillingReference (aUBLBillingRef);
      }
    }

    // DespatchDocumentReference
    {
      final DocumentReferenceType aUBLDocRef = _convertDocumentReference (aDelivery.getDespatchAdviceReferencedDocument ());
      if (aUBLDocRef != null)
        aUBLInvoice.addDespatchDocumentReference (aUBLDocRef);
    }

    // ReceiptDocumentReference
    {
      final DocumentReferenceType aUBLDocRef = _convertDocumentReference (aDelivery.getReceivingAdviceReferencedDocument ());
      if (aUBLDocRef != null)
        aUBLInvoice.addReceiptDocumentReference (aUBLDocRef);
    }

    // OriginatorDocumentReference
    {
      for (final ReferencedDocumentType aRD : aAgreement.getAdditionalReferencedDocument ())
      {
        // Use for "Tender or lot reference" with TypeCode "50"
        if ("50".equals (aRD.getTypeCodeValue ()))
        {
          final DocumentReferenceType aUBLDocRef = _convertDocumentReference (aRD);
          if (aUBLDocRef != null)
            aUBLInvoice.addOriginatorDocumentReference (aUBLDocRef);
        }
      }
    }

    // ContractDocumentReference
    {
      final DocumentReferenceType aUBLDocRef = _convertDocumentReference (aAgreement.getContractReferencedDocument ());
      if (aUBLDocRef != null)
        aUBLInvoice.addContractDocumentReference (aUBLDocRef);
    }

    // AdditionalDocumentReference
    {
      for (final ReferencedDocumentType aRD : aAgreement.getAdditionalReferencedDocument ())
      {
        // Except OriginatorDocumentReference
        if (!"50".equals (aRD.getTypeCodeValue ()))
        {
          final DocumentReferenceType aUBLDocRef = _convertDocumentReference (aRD);
          if (aUBLDocRef != null)
            aUBLInvoice.addAdditionalDocumentReference (aUBLDocRef);
        }
      }
    }

    // ProjectReference
    {
      final ProcuringProjectType aSpecifiedProcuring = aAgreement.getSpecifiedProcuringProject ();
      if (aSpecifiedProcuring != null)
      {
        final String sID = aSpecifiedProcuring.getIDValue ();
        if (StringHelper.hasText (sID))
        {
          final ProjectReferenceType aUBLProjectRef = new ProjectReferenceType ();
          aUBLProjectRef.setID (sID);
          aUBLInvoice.addProjectReference (aUBLProjectRef);
        }
      }
    }

    // Supplier Party
    {
      final TradePartyType aSellerParty = aAgreement.getSellerTradeParty ();
      if (aSellerParty != null)
      {
        final PartyType aUBLParty = _convertParty (aSellerParty);

        for (final TaxRegistrationType aTaxRegistration : aSellerParty.getSpecifiedTaxRegistration ())
          aUBLParty.addPartyTaxScheme (_convertPartyTaxScheme (aTaxRegistration));

        final PartyLegalEntityType aUBLPartyLegalEntity = _convertPartyLegalEntity (aSellerParty);
        if (aUBLPartyLegalEntity != null)
          aUBLParty.addPartyLegalEntity (aUBLPartyLegalEntity);

        final ContactType aUBLContact = _convertContact (aSellerParty);
        if (aUBLContact != null)
          aUBLParty.setContact (aUBLContact);

        aUBLSupplier.setParty (aUBLParty);
      }
    }

    // Customer Party
    {
      final TradePartyType aBuyerParty = aAgreement.getBuyerTradeParty ();
      if (aBuyerParty != null)
      {
        final PartyType aUBLParty = _convertParty (aBuyerParty);

        for (final TaxRegistrationType aTaxRegistration : aBuyerParty.getSpecifiedTaxRegistration ())
          aUBLParty.addPartyTaxScheme (_convertPartyTaxScheme (aTaxRegistration));

        final PartyLegalEntityType aUBLPartyLegalEntity = _convertPartyLegalEntity (aBuyerParty);
        if (aUBLPartyLegalEntity != null)
          aUBLParty.addPartyLegalEntity (aUBLPartyLegalEntity);

        final ContactType aUBLContact = _convertContact (aBuyerParty);
        if (aUBLContact != null)
          aUBLParty.setContact (aUBLContact);

        aUBLCustomer.setParty (aUBLParty);
      }
    }

    // Payee Party
    {
      final TradePartyType aPayeeParty = aSettlement.getPayeeTradeParty ();
      if (aPayeeParty != null)
      {
        final PartyType aUBLParty = _convertParty (aPayeeParty);

        for (final TaxRegistrationType aTaxRegistration : aPayeeParty.getSpecifiedTaxRegistration ())
          aUBLParty.addPartyTaxScheme (_convertPartyTaxScheme (aTaxRegistration));

        final PartyLegalEntityType aUBLPartyLegalEntity = _convertPartyLegalEntity (aPayeeParty);
        if (aUBLPartyLegalEntity != null)
          aUBLParty.addPartyLegalEntity (aUBLPartyLegalEntity);

        final ContactType aUBLContact = _convertContact (aPayeeParty);
        if (aUBLContact != null)
          aUBLParty.setContact (aUBLContact);

        aUBLInvoice.setPayeeParty (aUBLParty);
      }
    }

    // Tax Representative Party
    {
      final TradePartyType aTaxRepresentativeParty = aAgreement.getSellerTaxRepresentativeTradeParty ();
      if (aTaxRepresentativeParty != null)
      {
        final PartyType aUBLParty = _convertParty (aTaxRepresentativeParty);

        for (final TaxRegistrationType aTaxRegistration : aTaxRepresentativeParty.getSpecifiedTaxRegistration ())
          aUBLParty.addPartyTaxScheme (_convertPartyTaxScheme (aTaxRegistration));

        final PartyLegalEntityType aUBLPartyLegalEntity = _convertPartyLegalEntity (aTaxRepresentativeParty);
        if (aUBLPartyLegalEntity != null)
          aUBLParty.addPartyLegalEntity (aUBLPartyLegalEntity);

        final ContactType aUBLContact = _convertContact (aTaxRepresentativeParty);
        if (aUBLContact != null)
          aUBLParty.setContact (aUBLContact);

        aUBLInvoice.setTaxRepresentativeParty (aUBLParty);
      }
    }

    // Delivery
    {
      final TradePartyType aShipToParty = aDelivery.getShipToTradeParty ();
      if (aShipToParty != null)
      {
        final DeliveryType aUBLDelivery = new DeliveryType ();

        final SupplyChainEventType aSCE = aDelivery.getActualDeliverySupplyChainEvent ();
        if (aSCE != null)
        {
          final DateTimeType aODT = aSCE.getOccurrenceDateTime ();
          if (aODT != null)
            aUBLDelivery.setActualDeliveryDate (_parseDateDDMMYYYY (aODT.getDateTimeStringValue ()));
        }

        final LocationType aUBLDeliveryLocation = new LocationType ();
        boolean bUseLocation = false;

        final oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.IDType aUBLID = _extractPartyID (aShipToParty);
        if (aUBLID != null)
        {
          aUBLDeliveryLocation.setID (aUBLID);
          bUseLocation = true;
        }

        final TradeAddressType aPostalAddress = aShipToParty.getPostalTradeAddress ();
        if (aPostalAddress != null)
        {
          aUBLDeliveryLocation.setAddress (_convertPostalAddress (aPostalAddress));
          bUseLocation = true;
        }

        if (bUseLocation)
          aUBLDelivery.setDeliveryLocation (aUBLDeliveryLocation);

        final TextType aName = aShipToParty.getName ();
        if (aName != null)
        {
          final PartyType aUBLDeliveryParty = new PartyType ();
          final PartyNameType aUBLPartyName = new PartyNameType ();
          aUBLPartyName.setName (_copyName (aName));
          aUBLDeliveryParty.addPartyName (aUBLPartyName);
          aUBLDelivery.setDeliveryParty (aUBLDeliveryParty);
        }

        aUBLInvoice.addDelivery (aUBLDelivery);
      }
    }

    // Payment means
    {
      for (final TradeSettlementPaymentMeansType aPaymentMeans : aSettlement.getSpecifiedTradeSettlementPaymentMeans ())
      {
        final PaymentMeansType aUBLPaymentMeans = new PaymentMeansType ();

        final PaymentMeansCodeType aUBLPaymentMeansCode = new PaymentMeansCodeType ();
        aUBLPaymentMeansCode.setValue (aPaymentMeans.getTypeCodeValue ());
        if (aPaymentMeans.hasInformationEntries ())
          aUBLPaymentMeansCode.setName (aPaymentMeans.getInformationAtIndex (0).getValue ());
        aUBLPaymentMeans.setPaymentMeansCode (aUBLPaymentMeansCode);

        for (final TextType aPaymentRef : aSettlement.getPaymentReference ())
        {
          final PaymentIDType aUBLPaymentID = new PaymentIDType ();
          aUBLPaymentID.setValue (aPaymentRef.getValue ());
          aUBLPaymentMeans.addPaymentID (aUBLPaymentID);
        }

        final TradeSettlementFinancialCardType aCard = aPaymentMeans.getApplicableTradeSettlementFinancialCard ();
        if (aCard != null)
        {
          final CardAccountType aUBLCardAccount = new CardAccountType ();
          aUBLCardAccount.setPrimaryAccountNumberID (_copyID (aCard.getID (), new PrimaryAccountNumberIDType ()));
          // No CII field present
          aUBLCardAccount.setNetworkID ("mapped-from-cii");
          aUBLCardAccount.setHolderName (aCard.getCardholderNameValue ());
          aUBLPaymentMeans.setCardAccount (aUBLCardAccount);
        }

        final CreditorFinancialAccountType aAccount = aPaymentMeans.getPayeePartyCreditorFinancialAccount ();
        final CreditorFinancialInstitutionType aInstitution = aPaymentMeans.getPayeeSpecifiedCreditorFinancialInstitution ();
        if (aAccount != null || aInstitution != null)
        {
          final FinancialAccountType aUBLFinancialAccount = new FinancialAccountType ();
          if (aAccount != null)
          {
            aUBLFinancialAccount.setID (_copyID (aAccount.getIBANID ()));
            aUBLFinancialAccount.setName (_copyName (aAccount.getAccountName ()));
          }
          if (aInstitution != null)
          {
            final BranchType aUBLBranch = new BranchType ();
            aUBLBranch.setID (_copyID (aInstitution.getBICID ()));
            aUBLFinancialAccount.setFinancialInstitutionBranch (aUBLBranch);
          }
          aUBLPaymentMeans.setPayeeFinancialAccount (aUBLFinancialAccount);
        }

        {
          boolean bUseMandate = false;
          final PaymentMandateType aUBLPaymentMandate = new PaymentMandateType ();

          for (final TradePaymentTermsType aPaymentTerms : aSettlement.getSpecifiedTradePaymentTerms ())
          {
            if (aPaymentTerms.hasDirectDebitMandateIDEntries ())
            {
              aUBLPaymentMandate.setID (_copyID (aPaymentTerms.getDirectDebitMandateIDAtIndex (0)));
              bUseMandate = true;
              break;
            }
          }

          final IDType aCreditorRefID = aSettlement.getCreditorReferenceID ();
          if (aCreditorRefID != null)
          {
            final FinancialAccountType aUBLFinancialAccount = new FinancialAccountType ();
            aUBLFinancialAccount.setID (_copyID (aCreditorRefID));
            aUBLPaymentMandate.setPayerFinancialAccount (aUBLFinancialAccount);
            bUseMandate = true;
          }

          if (bUseMandate)
            aUBLPaymentMeans.setPaymentMandate (aUBLPaymentMandate);
        }

        aUBLInvoice.addPaymentMeans (aUBLPaymentMeans);
      }
    }

    // TODO
    return aUBLInvoice;
  }

  /**
   * Convert CII to UBL
   *
   * @param aFile
   *        Source file with CII to be parsed. May not be <code>null</code>.
   * @param aErrorList
   *        Error list to be filled. May not be <code>null</code>.
   * @return The parsed {@link InvoiceType} or {@link CreditNoteType}. May be
   *         <code>null</code> in case of error.
   */
  @Nullable
  public Serializable convertCIItoUBL (@Nonnull final File aFile, @Nonnull final ErrorList aErrorList)
  {
    final CrossIndustryInvoiceType aCIIInvoice = CIID16BReader.crossIndustryInvoice ()
                                                              .setValidationEventHandler (new WrappedCollectingValidationEventHandler (aErrorList))
                                                              .read (aFile);
    if (aCIIInvoice == null)
      return null;

    final TradeSettlementHeaderMonetarySummationType aTotal = aCIIInvoice.getSupplyChainTradeTransaction ()
                                                                         .getApplicableHeaderTradeSettlement ()
                                                                         .getSpecifiedTradeSettlementHeaderMonetarySummation ();
    final AmountType aDuePayable = aTotal == null ? null : aTotal.getDuePayableAmount ().get (0);

    if (aDuePayable == null || MathHelper.isGE0 (aDuePayable.getValue ()))
    {
      final InvoiceType aUBLInvoice = _convertToInvoice (aCIIInvoice, aErrorList);
      // TODO
      return aUBLInvoice;
    }

    LOGGER.info ("CreditNote is not yet supported");
    // Credit note
    // TODO
    return null;
  }
}
