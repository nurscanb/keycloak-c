import {useTranslation} from "react-i18next";
import {Controller, FormProvider, SubmitHandler, useForm, useFormContext} from "react-hook-form";
import {FormGroup, TextInput, ValidatedOptions} from "@patternfly/react-core";
import {HelpItem} from "@keycloak/keycloak-ui-shared";
// import {KeycloakTextInput} from "../../../components/keycloak-text-input/KeycloakTextInput";
import React, {ChangeEvent, ChangeEventHandler, FormEvent, ReactNode, useEffect, useState} from "react";

import {Form} from "react-router-dom";
import {AdminClientProps} from "../../../admin-client";


interface BackendUrlFieldProps {
    id:string,
    urlRequired?:boolean,
    onValueChange: (value: string) => void;
}

const BackendUrlField  : React.FC<BackendUrlFieldProps> = ({id, urlRequired,onValueChange}) =>{

    // const {adminClient} = this.props;
    const {t} = useTranslation("identity-providers");
    const {
        register,
        watch,
        formState: { errors },
    } = useFormContext();

    const backendUrl = watch("backendUrl");

    const [val,setVal]=useState<string>('');
    const handleInputChange = (event: ChangeEvent<HTMLInputElement>)=>{
        const value = event.target.value;
        setVal(value);
        localStorage.setItem(backendUrl, value);
        // onValueChange(value);

        //setVal(event.target.value);
    }

    useEffect( () => {
        const storedValue = localStorage.getItem(backendUrl);
        if (storedValue) {
            try {
                setVal(storedValue);
                onValueChange(storedValue);
                //mainFunction(storedValue);
                console.log(`storedvalue funct..  ${storedValue}`)

            }catch (error){
                console.error('Ana fonksiyon calistirilirken bir hata olustu:', error);
            }
        }else {
            console.error('LocalStorage\'dan deger alinamadi.');
        }

    }, []);

    // const getValueFromField = async () =>{
    //     console.log(backendUrl);
    //
    //     try {
    //         const result = await this.props.adminClient.identityProviders.importFromUrl  ({
    //             providerId: id,
    //             fromUrl: backendUrl,
    //         });
    //         console.log(result);
    //         //mainFunction(result);
    //
    //     }catch (error){
    //         console.error(`getValueFromField funct bir hata olustu ${error}`);
    //     }
    //
    // };
    const mainFunction = async (fieldInput:string) => {
        const accessToken = await getTokenFromKeycloak();
        if (accessToken) {
            await sendRequestToBackend(accessToken,fieldInput);
        } else {
            console.error('Token alinamadi.... use effect funct icin');
        }
    };


    const getTokenFromKeycloak = async ()=> {
        const grantType= "password";
        const username = "admin";
        const password = "admin";
        const clientId = "admin-cli"; // Keycloak istemci kimligi

        try {
            const response = await fetch("http://0.0.0.0:8080/realms/master/protocol/openid-connect/token", {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                },
                body: `grant_type=${grantType}&username=${username}&password=${password}&client_id=${clientId}`
            });

            if (response.ok) {
                const data = await response.json();
                const accessToken = data.access_token;
                return accessToken;
            } else {
                console.error('Token alinamadi:', response);
                return null;
            }
        } catch (error) {
            console.error('Bir hata olutu:', error);
            return null;
        }
    }

    const sendRequestToBackend = async (accessToken:string,dynamicUrl:string) => {
        try {
            console.log(accessToken)
            const response = await fetch("http://0.0.0.0:8080/realms/master/login-actions/mobilsignBackend", {
                method: 'POST',
                headers: {
                    'Content-Type': 'text/plain'
                },
                body: `${dynamicUrl}`
            });
            if (response.ok) {
                const data = await response.json();
                console.log("Backend'den gelen veri:", data);
            } else {
                console.error('Hatali yanit alindi:', response);
            }
        } catch (error) {
            console.error('Bir hata olustu:', error);
        }
    };

    return(
        <>
            {/*<RedirectUrl id={id}/>*/}
            {/*<ClientIdSecret/>*/}
            <Form>
                <FormGroup
                    label={t("backendUrl")}
                    labelIcon={
                        <HelpItem
                            helpText={t("identity-providers-help:backendUrl")}
                            fieldLabelId="identity-providers:backend-url"
                        />
                    }
                    fieldId="kc-backend-url"
                    isRequired
                    // validated={
                    //     errors.config?.backendUrl
                    //         ? ValidatedOptions.error
                    //         : ValidatedOptions.default
                    // }
                    // helperTextInvalid={t("common:required")}
                >


                    <TextInput
                        isRequired
                        type="text"
                        id="kc-backend-url"
                        data-testid="backendUrl"
                        {...register("config.backendUrl", { required: true})}
                        //value={val}
                        onInput={handleInputChange}
                    />
                    <p>{val}vdbfgnhkl</p>
                </FormGroup>
            </Form>

        </>
    );

};

export {BackendUrlField};
